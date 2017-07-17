#!/bin/bash
# shellcheck disable=SC2015
#
# Script to configure an OS for use with Gluster
#
#################################################################
PROGNAME=$(basename "${0}")
# shellcheck disable=SC1091
source /etc/cfn/gluster.cfn_envs
ENDPTNAME="${GLUSTER_CFN_ENDPOINT:-UNDEF}"
FWPORTS=(
      111/udp
      875/tcp
      875/udp
      4501/tcp
   )
FWSVCS=(
      glusterfs
      mountd
   )
LOCALIP="$(curl http://169.254.169.254/latest/meta-data/local-ipv4)"
PARTNERIP="${GLUSTER_PARTNER_IP:-UNDEF}"
RESRCNAME="${GLUSTER_CFN_RESOURCE_NAME:-UNDEF}"
STACKNAME="${GLUSTER_CFN_STACK_NAME:-UNDEF}"


##
## Set up an error logging and exit-state
function err_exit {
   local ERRSTR="${1}"
   local SCRIPTEXIT=${2:-1}

   # Our output channels
   echo "${ERRSTR}" > /dev/stderr
   logger -t "${PROGNAME}" -p kern.crit "${ERRSTR}"

   # Need our exit to be an integer
   if [[ ${SCRIPTEXIT} =~ ^[0-9]+$ ]]
   then
      exit "${SCRIPTEXIT}"
   else
      exit 1
   fi
}

##
## Volume-setup tasks
function MkGlusterCfg {
   gluster peer probe "${PARTNERIP}"
   sleep 15

   printf "Creating 'gfsvolume' replicated Gluster volume... "
   gluster volume create gfsvolume replica 2 transport tcp \
      "${PARTNERIP}":/gfsbrick "${LOCALIP}":/gfsbrick force && \
      echo "Success!" || err_exit 'Failed to set up Gluster volume'

   printf "Starting 'gfsvolume' replicated Gluster volume... "
   gluster volume start gfsvolume && \
      echo "Success!" || err_exit 'Failed to start Gluster volume'

   echo "Setting volume-attributes on Gluster volume"
   gluster volume set gfsvolume auth.allow '*'
   gluster volume set gfsvolume features.cache-invalidation on
}

##
## Add firewall exceptions
function FwRules {
   for FWPORT in "${FWPORTS[@]}"
   do
      printf "Adding port exception(s) for %s... " "${FWPORT}"
      firewall-cmd --add-port="${FWPORT}" --permanent || \
         echo "Failed adding port exception(s) for ${FWPORT}"
   done

   for FWSVC in "${FWSVCS[@]}"
   do
      printf "Adding service exception(s) for %s... " "${FWSVC}"
      firewall-cmd --add-service="${FWSVC}" --permanent || \
         echo "Failed adding service exception(s) for ${FWSVC}"
   done

   printf "Reloading firewalld (to activate new rules)... "
   firewall-cmd --reload && echo "Success!" || \
      echo "Failed to re-read firewalld rules."
}


########################
## Main Program Logic ##
########################

# Update firewalld rules (adjusting SELinux as necessary)
GETSELSTATE=$(getenforce)
if [[ ${GETSELSTATE} = Enforcing ]]
then
   setenforce Permissive || echo "Couldn't dial-back SELinux"
   FwRules
   setenforce "${GETSELSTATE}" || echo "Couldn't reset SELinux"
else
   FwRules
fi

if [[ ${PARTNERIP} = UNDEF ]]
then
   echo "Node is first cluster-member: no further tasks to complete"
else
   MkGlusterCfg
fi

# Signal Cfn that we're good\n",
/opt/aws/bin/cfn-signal -e 0 --stack "${STACKNAME}" \
   --resource "${RESRCNAME}" \
   --url "${ENDPTNAME}"

# Clear ourselves from rc.local
sed -i '/'"${PROGNAME}"'/d' /etc/rc.d/rc.local
chmod 000644 /etc/rc.d/rc.local

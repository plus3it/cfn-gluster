#!/bin/bash
# shellcheck disable=SC2015
#
# Script to configure an OS for use with Gluster
#
#################################################################
PROGNAME=$(basename "${0}")
GFSCONFIG=${1:-UNDEF}
# shellcheck disable=SC1091
source /etc/cfn/gluster.cfn_envs
BRICKPATH=/gfsbrick
CHKFIPS=/proc/sys/crypto/fips_enabled
GLUSTERDEV=${GLUSTER_DEVICE:-UNDEF}
GLUSTERRPMS=(
      glusterfs
      gluster-cli
      glusterfs-libs
      glusterfs-server
   )
LOCALIP="$(curl http://169.254.169.254/latest/meta-data/local-ipv4)"
OSRELRPMS=(
      redhat-release
      centos-release
   )
PARTNERIP="${GLUSTER_PARTNER_IP:-UNDEF}"


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
## Disable FIPS mode
function FipsDisable {
   printf "Removing FIPS kernel RPMs... "
   yum -q remove -y dracut-fips\* &&
      echo "Success!" || err_exit 'Failed to remove FIPS kernel RPMs'

   printf "Moving FIPSed initrafmfs aside... "
   mv -v /boot/initramfs-"$(uname -r)".img{,.FIPS-bak} &&
      echo "Success!" || err_exit 'Failed to move FIPSed initrafmfs aside'

   echo "Generating de-FIPSed initramfs"
   dracut -v || err_exit 'Error encountered during regeeration of initramfs'

   echo "Removing 'fips' kernel arguments from GRUB config"
   grubby --update-kernel=ALL --remove-args=fips=1
   [[ -f /etc/default/grub ]] && sed -i 's/ fips=1//' /etc/default/grub

   shutdown -r +1 'Rebooting to complete disablement of FIPS mode'
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


########################
## Main Program Logic ##
########################

# Determine which reponame to use for Gluster RPMs
case $(rpm --qf '%{name}' -qa "${OSRELRPMS[@]}") in
   redhat-release)
      printf "Activating Red Hat Gluster repository... "
      yum install -q -y redhat-release-gluster &&
         echo "Success!" || \
         err_exit 'Failed to activate Red Hat Gluster repository'
      ;;
   centos-release)
      printf "Activating CentOS Gluster repository... "
      yum install -y centos-release-gluster &&
         echo "Success!" || \
         err_exit 'Failed to activate CentOS Gluster repository'
      ;;
   *)
      err_exit 'Cannot determine repo to load Gluster RPMs from'
      ;;
esac

# Install XFS-progs as needed
if [[ $(rpm --quiet -q xfsprogs)$? -ne 0 ]]
then
   echo "Ensuring XFS support is present"
   yum install -y xfsprogs || err_exit 'Could not add XFS support'
fi

# Format Gluster 'brick'
printf "Creating XFS filesystem on %s... " "${GLUSTERDEV}"
mkfs.xfs -q -i size=512 -n size=8192 "${GLUSTERDEV}" &&
   echo "Success!" || err_exit 'Filesystem creation failed'

# Ensure "${BRICKPATH}" exists
if [[ ! -d "${BRICKPATH}" ]]
then
   install -d -o root -g root -m 0700 "${BRICKPATH}" || \
      err_exit "Failed to create ${BRICKPATH}"
fi

# Add Gluster brick to fstab
printf "%s\t%s\txfs\tdefaults\t1 2\n" "${GLUSTERDEV}" "${BRICKPATH}" \
   >> /etc/fstab || err_exit "Failed to add ${GLUSTERDEV} fstab"
mount "${BRICKPATH}" || err_exit "Failed to mount ${BRICKPATH}"

# Install and enable Gluster components and services
echo "Adding Gluster and related packages... "
yum install -y "${GLUSTERRPMS[@]}" || \
   err_exit 'Failed installing Gluster RPMs'
systemctl enable glusterd || err_exit 'Failed enabling glusterd'
systemctl start glusterd || err_exit 'Failed starting glusterd'

# De-FIPS as necessary...
if [[ -f ${CHKFIPS} ]] && [[ $(grep -q 1 "${CHKFIPS}")$? -eq 0 ]]
then
   echo "FIPS mode is enabled. Attemtping to disable."
   FipsDisable

   if [[ -f ${GFSCONFIG} ]]
   then
      echo "Writing /etc/rc.d/rc.local"
      (
        echo "## Delete after this line..."
        echo "exec 2> /var/log/rc.local.log"
        echo "exec 1>&2"
        echo "printf 'Running ${GFSCONFIG}... '"
        echo "bash ${GFSCONFIG} && echo 'Success' || echo 'FAILED'"
        echo "echo 'Reverting rc.local contents to default'"
        echo "chmod 644 /etc/rc.d/rc.local"
        echo "sed -i '/## Delete after/,\$d' /etc/rc.d/rc.local"
      ) >> /etc/rc.d/rc.local
      chmod a+x /etc/rc.d/rc.local
   elif [[ ! -f ${GFSCONFIG} ]]
   then
      echo "${GFSCONFIG} is not a valid file-path"
   elif [[ ${GFSCONFIG} = UNDEF ]]
   then
      echo "No GFS-config script was specified."
   fi

elif [[ ${GFSCONFIG} = UNDEF ]]
then
   echo "No GFS-config script was specified."
else
   printf "Chaining config-mods to %s..." "${GFSCONFIG}"
   ${GFSCONFIG} || err_exit "${GFSCONFIG} exited abnormally."
fi

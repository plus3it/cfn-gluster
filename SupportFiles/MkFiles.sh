#!/bin/bash
#
# Simple script to exercise the shared filesystem
#
#################################################################
PROGNAME="$(basename "${0}")"
TESTDIR="${1:-/mnt}"

# Output errors to STDERR and syslog
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

# Simple nested loop to create 1MiB files with random contents
for DIRN in $( seq 0 5 )
do
   [[ ! -x ${TESTDIR}/test${DIRN}.d ]] && mkdir "${TESTDIR}/test${DIRN}.d"
   for HUNDREDS in $( seq 0 9 )
   do
      for TENS in $( seq 0 9 )
      do
         for ONES in $( seq 0 9 )
         do
            dd if=/dev/urandom bs=1024 \
              of="${TESTDIR}/test${DIRN}.d/test_file-${HUNDREDS}${TENS}${ONES}" \
              count=1024 || err_exit "Failed writing ${TESTDIR}/test${DIRN}.d/test_file-${HUNDREDS}${TENS}${ONES}"
         done
      done
   done
done

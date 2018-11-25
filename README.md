# Overview
This project is designed to facilitate the standing up of basic Gluster storage-clusters. Templates can be used to create a storage-cluster of arbitrary size and layout: 2-way and 3-way mirrored-configurations laid out across both two and three availability-zones have been tested. Templates deploy the cluster nodes using AWS's AutoScalingGroup functionality. In the event of a node-failure, the AutoScalingGroup will detect the failure and build a replacement node.

It is generally assumed that this automation will only be used:
* In regions where [EFS](https://aws.amazon.com/efs/) is not yet available
* In use-cases where EFS is insufficiently-performant (typically small- or medium-sized, active data-sets: see the vendor's [Performance](https://docs.aws.amazon.com/efs/latest/ug/performance.html) guidance-document)

## Notes

* This automation has only been tested using the [SPEL](https://github.com/plus3it/spel.git) CentOS 7 AMIs. However, should_ also work on instances built from MarketPlace or other CentOS 7 AMIs, both SPEL and generic/MarketPlace Red Hat 7 AMIs or Amazon Linux 2 AMIs.
* While these templates leverage [watchmaker](https://watchmaker.readthedocs.io) to apply STIG-hardening to the deployed instances, FIPS mode is specifically disabled. Node-recovery is adversely-impacted when nodes are run in FIPS-mode.
* Automated node-replacement: Gluster's in-built security prevents replacement-nodes from either being automatically-admitted to a degraded cluster (one missing one or more members due to failure) or automatically assuming the role of a failed node. 
    * It is _critical_ that the cluster be monitored for node-faults and rebuild-events &mdash; particularly if the cluster hosts critical data.
    * In the event of a node-fault/rebuild-event, it will be necessary for an administrator to log into a serviving node and execute cluster recovery procedures. The [documented procedures](docs/ClusterRecovery.md) describe how to admit a rebuilt node into the degraded-cluster and use it to replace a failed node.
* Backup-automation is not currently built in (though [is being considered](https://github.com/plus3it/cfn-gluster/issues/1) for future iterations)

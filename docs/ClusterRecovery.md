# Cluster Recovery After Node-Failure

The included automation deploys cluster-nodes using self-healing AutoScalingGroups (ASGs). In the event of a total node-failure, the ASG will automatically rebuild the failed node. While the replacement-node will be suitable to take the place of the failed node, it will differ from the failed node &mdash; having a new IP address, hostname, etc.

The Gluster software has a concept of trusted peers. When the replacement-node is provisioned, the surviving cluster node(s) will not trust it. The replacement-node will try to join the cluster but will be rejected by the surviving cluster node(s). It will be necessary to undertake some manual steps to integrate the replacement-node into the cluster:

## From the surviving node:

1. Verify the initial peer-status:

    ~~~~
    # gluster peer status
    Number of Peers: 1
    
    Hostname: 10.6.80.122
    Uuid: ce3710ea-2918-4d53-a86f-ac418608578b
    State: Peer in Cluster (Disconnected)
    ~~~~

1. Verify the volume-status:

 
    ~~~~
    # gluster volume heal vol01 info summary
    Brick 10.6.80.122:/bricks/vol01/shared
    Status: Transport endpoint is not connected
    Total Number of entries: -
    Number of entries in heal pending: -
    Number of entries in split-brain: -
    Number of entries possibly healing: -
    
    Brick 10.6.17.110:/bricks/vol01/shared
    Status: Connected
    Total Number of entries: 0
    Number of entries in heal pending: 0
    Number of entries in split-brain: 0
    Number of entries possibly healing: 0
    ~~~~

1. Verify that new peer is online and reachable:

    ~~~~
    ( export PEER=<PEER_IP | PEER_FQDN>; timeout 1 bash -c \
        "cat < /dev/null > /dev/tcp/${PEER}/24007" && \
          echo "${PEER} is online" || echo echo "${PEER} is offline" )
    ~~~~

1. Add new peer to cluster trust-list:

    ~~~~
    [root@ip-10-6-17-110 ~]# gluster peer probe ip-10-6-78-144.ec2.internal
    peer probe: success.
    ~~~~

1. Verify new peer is in cluster trust-list:

    ~~~~
    # gluster peer status
    Number of Peers: 2
    
    Hostname: 10.6.80.122
    Uuid: ce3710ea-2918-4d53-a86f-ac418608578b
    State: Peer in Cluster (Disconnected)
    
    Hostname: ip-10-6-78-144.ec2.internal
    Uuid: da4a0e87-0310-403c-9573-e067a48859ae
    State: Peer in Cluster (Connected)
    ~~~~

1. Substitute new node for failed node:

    ~~~~
    # gluster volume replace-brick vol01 10.6.80.122:/bricks/vol01/shared \
      ip-10-6-78-144.ec2.internal:/bricks/vol01/shared commit force
    volume replace-brick: success: replace-brick commit force operation successful
    ~~~~

1. Verify that cluster is "healing" &mdash; do two status-checks and verify that the number of pending heal entries is reducing:

    ~~~~
    # gluster volume heal vol01 info summary
    Brick ip-10-6-78-144.ec2.internal:/bricks/vol01/shared
    Status: Connected
    Total Number of entries: 0
    Number of entries in heal pending: 0
    Number of entries in split-brain: 0
    Number of entries possibly healing: 0
    
    Brick 10.6.17.110:/bricks/vol01/shared
    Status: Connected
    Total Number of entries: 6783
    Number of entries in heal pending: 6783
    Number of entries in split-brain: 0
    Number of entries possibly healing: 0
    
    # gluster volume heal vol01 info summary
    Brick ip-10-6-78-144.ec2.internal:/bricks/vol01/shared
    Status: Connected
    Total Number of entries: 0
    Number of entries in heal pending: 0
    Number of entries in split-brain: 0
    Number of entries possibly healing: 0
    
    Brick 10.6.17.110:/bricks/vol01/shared
    Status: Connected
    Total Number of entries: 5732
    Number of entries in heal pending: 5732
    Number of entries in split-brain: 0
    Number of entries possibly healing: 0
    ~~~~

1. If automatic healing is not yet occuring, forcibly start the heal:

    ~~~~
    # gluster volume heal vol01
    Launching heal operation to perform index self heal on volume vol01 has been successful
    Use heal info commands to check status.
    ~~~~

1. Remove the failed node from the cluster's trust-list

    ~~~~
    # gluster peer status
    Number of Peers: 2
    
    Hostname: 10.6.80.122
    Uuid: ce3710ea-2918-4d53-a86f-ac418608578b
    State: Peer in Cluster (Disconnected)
    
    Hostname: ip-10-6-78-144.ec2.internal
    Uuid: da4a0e87-0310-403c-9573-e067a48859ae
    State: Peer in Cluster (Connected)
    
    # gluster peer detach 10.6.80.122
    peer detach: success
    
    # gluster peer status
    Number of Peers: 1
    
    Hostname: ip-10-6-78-144.ec2.internal
    Uuid: da4a0e87-0310-403c-9573-e067a48859ae
    State: Peer in Cluster (Connected)
    ~~~~

## From any cluster node:

The amount of time necessary for the heal operation to complete will be proportional to the amount of data to replicate. The following steps assume that sufficient time has been allowed for that operation to complete. These steps allow an administrator to verify that the cluster is, indeed, back to a fully-operational, synchronized and sane state:

1. Verify that the heal operation has completed (all counters should be `0`):

    ~~~~
    # gluster volume heal vol01 info summary
    Brick ip-10-6-78-144.ec2.internal:/bricks/vol01/shared
    Status: Connected
    Total Number of entries: 0
    Number of entries in heal pending: 0
    Number of entries in split-brain: 0
    Number of entries possibly healing: 0
    
    Brick 10.6.17.110:/bricks/vol01/shared
    Status: Connected
    Total Number of entries: 0
    Number of entries in heal pending: 0
    Number of entries in split-brain: 0
    Number of entries possibly healing: 0
    ~~~~

1. Verify that the heal operation has completed (both node's storage statistics should be equal):

    ~~~~
    # gluster volume status vol01 detail
    Status of volume: vol01
    ------------------------------------------------------------------------------
    Brick                : Brick ip-10-6-78-144.ec2.internal:/bricks/vol01/shared
    TCP Port             : 49152
    RDMA Port            : 0
    Online               : Y
    Pid                  : 1681
    File System          : ext4
    Device               : /dev/nvme1n1
    Mount Options        : rw,seclabel,noatime,data=ordered
    Inode Size           : 512
    Disk Space Free      : 2.7GB
    Total Disk Space     : 9.6GB
    Inode Count          : 655360
    Free Inodes          : 641420
    ------------------------------------------------------------------------------
    Brick                : Brick 10.6.17.110:/bricks/vol01/shared
    TCP Port             : 49152
    RDMA Port            : 0
    Online               : Y
    Pid                  : 1365
    File System          : ext4
    Device               : /dev/nvme1n1
    Mount Options        : rw,seclabel,noatime,data=ordered
    Inode Size           : 512
    Disk Space Free      : 2.7GB
    Total Disk Space     : 9.6GB
    Inode Count          : 655360
    Free Inodes          : 641419
    ~~~~

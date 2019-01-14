pipeline {

    agent any

    options {
        buildDiscarder(
            logRotator(
                numToKeepStr: '5',
                daysToKeepStr: '30',
                artifactDaysToKeepStr: '30',
                artifactNumToKeepStr: '3'
            )
        )
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
    }

    environment {
        AWS_DEFAULT_REGION = "${AwsRegion}"
        AWS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
        REQUESTS_CA_BUNDLE = '/etc/pki/tls/certs/ca-bundle.crt'
    }

    parameters {
        string(name: 'AwsRegion', defaultValue: 'us-east-1', description: 'Amazon region to deploy resources into')
        string(name: 'AwsCred', description: 'Jenkins-stored AWS credential with which to execute cloud-layer commands')
        string(name: 'GitCred', description: 'Jenkins-stored Git credential with which to execute git commands')
        string(name: 'GitProjUrl', description: 'SSH URL from which to download the Jenkins git project')
        string(name: 'GitProjBranch', description: 'Project-branch to use from the Jenkins git project')
        string(name: 'CfnStackRoot', description: 'Unique token to prepend to all stack-element names')
        string(name: 'AdminPubkeyURL', description: '(Optional) URL of file containing admin groups SSH public-keys')
        string(name: 'AmiId', description: 'ID of the AMI to launch')
        string(name: 'BackupBucketName', description: 'Name of S3 Bucket used to host Gluster backups')
        string(name: 'CfnGetPipUrl', description: 'URL to get-pip.py')
        string(name: 'CloudWatchAgentUrl', description: '(Optional) S3 URL to CloudWatch Agent installer', defaultValue: 's3://amazoncloudwatch-agent/linux/amd64/latest/AmazonCloudWatchAgent.zip')
        string(name: 'CloudwatchBucketName', description: 'Name of the S3 Bucket hosting the CloudWatch agent archive files')
        string(name: 'DistributionNum', description: 'Number of distribution-nodes.(1-9)')
        string(name: 'GlusterReleasePkg', description: 'Name of the gluster repository-definition RPM containing the desired GlusterFS packages', defaultValue: 'centos-release-gluster5')
        string(name: 'GlusFleetTemplateUri', description: 'URI for the template that creates Glusters EC2 node-components')
        string(name: 'IamTemplateUri', description: 'URI for the template that creates Gluster IAM components')
        string(name: 'InstanceType', description: 'GlusterFS node EC2 instance type')
        string(name: 'KeyName', description: 'Name of an existing EC2 KeyPair to enable (single-user) SSH access to the instances')
        string(name: 'MgtSgs', description: 'Security group(s) that need management access to the Gluster nodes CLIs')
        string(name: 'ProvisionUser', description: 'Alphanumeric string between 6 and 12 characters', defaultValue: 'ec2-user')
        string(name: 'PypiIndexUrl', description: 'URL to the PyPi Index', defaultValue: 'https://pypi.org/simple')
        string(name: 'ReplicaNum', description: 'Number of replica.(1-9; 1 means no replicas)', defaultValue: '3')
        string(name: 'RolePrefix', description: 'Prefix to apply to IAM role to make things a bit prettier (optional)')
        string(name: 'RootVolumeSize', description: 'Size in GB of the fleet-node root EBS volumes', defaultValue: '30')
        string(name: 'SgTemplateUri', description: 'URI for the template that creates Glusters SecurityGroup components')
        string(name: 'SharedVolumeSize', description: 'Size in GB of the EBS volume to create')
        string(name: 'SharedVolumeType', description: 'Type of EBS volume to create', defaultValue: 'gp2')
        string(name: 'SubnetList', description: 'Subnets to which GlusterFS nodes may belong')
        string(name: 'TargetVPC', description: 'ID of the VPC to deploy Confluence components into')
        string(name: 'WatchmakerAdminGroups', description: '(Optional) Colon-separated list of domain groups that should have admin permissions on the EC2 instance')
        string(name: 'WatchmakerAdminUsers', description: '(Optional) Colon-separated list of domain users that should have admin permissions on the EC2 instance')
        string(name: 'WatchmakerAvailable', description: 'Specify if Watchmaker is available (if "false" all other Watchmaker-related parms will be ignored)')
        string(name: 'WatchmakerComputerName', description: '(Optional) Sets the hostname/computername within the fleet-nodes OSes')
        string(name: 'WatchmakerConfig', description: '(Optional) URL to a Watchmaker config file')
        string(name: 'WatchmakerEnvironment', description: 'Environment in which the instance is being deployed', defaultValue: 'dev')
        string(name: 'WatchmakerOuPath', description: '(Optional) DN of the OU to place the instance when joining a domain. If blank and "WatchmakerEnvironment" enforces a domain join, the instance will be placed in a default container. Leave blank if not joining a domain, or if "WatchmakerEnvironment" is "false"')
        string(name: 'WatchmakerS3Source', description: 'Flag that tells watchmaker to use its instance role to retrieve watchmaker content from S3')
    }

    stages {
        stage ('Prepare Agent Environment') {
            steps {
                deleteDir()
                git branch: "${GitProjBranch}",
                    credentialsId: "${GitCred}",
                    url: "${GitProjUrl}"
                writeFile file: 'gitlab-gluster_fleet.parms.json',
                    text: /
                        [
                            {
                                "ParameterKey": "AdminPubkeyURL",
                                "ParameterValue": "${env.AdminPubkeyURL}"
                            },
                            {
                                "ParameterKey": "AmiId",
                                "ParameterValue": "${env.AmiId}"
                            },
                            {
                                "ParameterKey": "BackupBucketName",
                                "ParameterValue": "${env.BackupBucketName}"
                            },
                            {
                                "ParameterKey": "CfnGetPipUrl",
                                "ParameterValue": "${env.CfnGetPipUrl}"
                            },
                            {
                                "ParameterKey": "CloudWatchAgentUrl",
                                "ParameterValue": "${env.CloudWatchAgentUrl}"
                            },
                            {
                                "ParameterKey": "CloudwatchBucketName",
                                "ParameterValue": "${env.CloudwatchBucketName}"
                            },
                            {
                                "ParameterKey": "DistributionNum",
                                "ParameterValue": "${env.DistributionNum}"
                            },
                            {
                                "ParameterKey": "GlusFleetTemplateUri",
                                "ParameterValue": "${env.GlusFleetTemplateUri}"
                            },
                            {
                                "ParameterKey": "GlusterReleasePkg",
                                "ParameterValue": "${env.GlusterReleasePkg}"
                            },
                            {
                                "ParameterKey": "IamTemplateUri",
                                "ParameterValue": "${env.IamTemplateUri}"
                            },
                            {
                                "ParameterKey": "InstanceType",
                                "ParameterValue": "${env.InstanceType}"
                            },
                            {
                                "ParameterKey": "KeyName",
                                "ParameterValue": "${env.KeyName}"
                            },
                            {
                                "ParameterKey": "MgtSgs",
                                "ParameterValue": "${env.MgtSgs}"
                            },
                            {
                                "ParameterKey": "ProvisionUser",
                                "ParameterValue": "${env.ProvisionUser}"
                            },
                            {
                                "ParameterKey": "PypiIndexUrl",
                                "ParameterValue": "${env.PypiIndexUrl}"
                            },
                            {
                                "ParameterKey": "ReplicaNum",
                                "ParameterValue": "${env.ReplicaNum}"
                            },
                            {
                                "ParameterKey": "RolePrefix",
                                "ParameterValue": "${env.RolePrefix}"
                            },
                            {
                                "ParameterKey": "RootVolumeSize",
                                "ParameterValue": "${env.RootVolumeSize}"
                            },
                            {
                                "ParameterKey": "SgTemplateUri",
                                "ParameterValue": "${env.SgTemplateUri}"
                            },
                            {
                                "ParameterKey": "SharedVolumeSize",
                                "ParameterValue": "${env.SharedVolumeSize}"
                            },
                            {
                                "ParameterKey": "SharedVolumeType",
                                "ParameterValue": "${env.SharedVolumeType}"
                            },
                            {
                                "ParameterKey": "SubnetList",
                                "ParameterValue": "${env.SubnetList}"
                            },
                            {
                                "ParameterKey": "TargetVPC",
                                "ParameterValue": "${env.TargetVPC}"
                            },
                            {
                                "ParameterKey": "WatchmakerAdminGroups",
                                "ParameterValue": "${env.WatchmakerAdminGroups}"
                            },
                            {
                                "ParameterKey": "WatchmakerAdminUsers",
                                "ParameterValue": "${env.WatchmakerAdminUsers}"
                            },
                            {
                                "ParameterKey": "WatchmakerAvailable",
                                "ParameterValue": "${env.WatchmakerAvailable}"
                            },
                            {
                                "ParameterKey": "WatchmakerComputerName",
                                "ParameterValue": "${env.WatchmakerComputerName}"
                            },
                            {
                                "ParameterKey": "WatchmakerConfig",
                                "ParameterValue": "${env.WatchmakerConfig}"
                            },
                            {
                                "ParameterKey": "WatchmakerEnvironment",
                                "ParameterValue": "${env.WatchmakerEnvironment}"
                            },
                            {
                                "ParameterKey": "WatchmakerOuPath",
                                "ParameterValue": "${env.WatchmakerOuPath}"
                            },
                            {
                                "ParameterKey": "WatchmakerS3Source",
                                "ParameterValue": "${env.WatchmakerS3Source}"
                            }
                        ]
                    /
                sh '''#!/bin/bash
                    printf "Validating param-file syntax... "
                    SYNTAXCHK=$( python -m json.tool gitlab-gluster_fleet.parms.json > /dev/null 2>&1 )$?
                    if [[ ${SYNTAXCHK} -eq 0 ]]
                    then
                       echo "syntax is valid"
                    else
                       echo "syntax not valid"
                       exit ${SYNTAXCHK}
                    fi
                '''
            }
        }
        stage ('Prepare AWS Environment') {
            steps {
                withCredentials(
                    [
                        [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                    ]
                ) {
                    sh '''#!/bin/bash
                        echo "Attempting to delete any active ${CfnStackRoot} stacks... "
                        aws --region "${AwsRegion}" cloudformation delete-stack --stack-name "${CfnStackRoot}"

                        sleep 5

                        # Pause if delete is slow
                        while [[ $(
                                    aws cloudformation describe-stacks \
                                      --stack-name ${CfnStackRoot} \
                                      --query 'Stacks[].{Status:StackStatus}' \
                                      --out text 2> /dev/null | \
                                    grep -q DELETE_IN_PROGRESS
                                   )$? -eq 0 ]]
                        do
                           echo "Waiting for stack ${CfnStackRoot} to delete..."
                           sleep 30
                        done
                    '''

                }
            }
        }
        stage ('Launch Nested Stack') {
            steps {
                withCredentials(
                    [
                        [$class: 'AmazonWebServicesCredentialsBinding', accessKeyVariable: 'AWS_ACCESS_KEY_ID', credentialsId: "${AwsCred}", secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']
                    ]
                ) {
                    sh '''#!/bin/bash
                        echo "Attempting to create stack ${CfnStackRoot}..."
                        aws --region "${AwsRegion}" cloudformation create-stack --stack-name "${CfnStackRoot}" \
                          --disable-rollback --capabilities CAPABILITY_NAMED_IAM \
                          --template-body file://Templates/make_GlusterCluster_parent.tmplt.json \
                          --parameters file://gitlab-gluster_fleet.parms.json

                        sleep 15

                        # Pause if create is slow
                        while [[ $(
                                    aws cloudformation describe-stacks \
                                      --stack-name ${CfnStackRoot} \
                                      --query 'Stacks[].{Status:StackStatus}' \
                                      --out text 2> /dev/null | \
                                    grep -q CREATE_IN_PROGRESS
                                   )$? -eq 0 ]]
                        do
                           echo "[$( date '+%Y-%m-%d %H:%M:%S' )] Waiting for stack ${CfnStackRoot} to finish create process..."
                           sleep 30
                        done

                        if [[ $(
                                aws cloudformation describe-stacks \
                                  --stack-name ${CfnStackRoot} \
                                  --query 'Stacks[].{Status:StackStatus}' \
                                  --out text 2> /dev/null | \
                                grep -q CREATE_COMPLETE
                               )$? -eq 0 ]]
                        then
                           echo "Stack-creation successful"
                        else
                           echo "Stack-creation ended with non-successful state"
                           exit 1
                        fi
                    '''
                }
            }
        }
    }
    post {
        cleanup {
           step([$class: 'WsCleanup'])
        }
    }
}

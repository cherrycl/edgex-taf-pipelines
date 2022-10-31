def main(CORE_SERVICES_RELEASE, COMPATIBLE_RELEASE, USE_APP_SERVICE, USE_DEVICE_SERVICE, CASE) {
    def profile = 'device-virtual'
    def USE_SECURITY = '-'
    def deploySuccess
    def BUSES = "REDIS,MQTT".split(',')

    if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
        USE_SECURITY = '-security-'
    }

    node("${NODE}") {
        stage ("${ARCH}${USE_SECURITY} ${CASE} Checkout edgex-taf repository") {
            checkout([$class: 'GitSCM',
                //branches: [[name: "*/main"]],
                branches: [[name: "*/fix-backward"]],
                doGenerateSubmoduleConfigurations: false,
                extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']],
                submoduleCfg: [],
                //userRemoteConfigs: [[url: 'https://github.com/edgexfoundry/edgex-taf.git']]
                userRemoteConfigs: [[url: 'https://github.com/cherrycl/edgex-taf.git']]
            ])
        }
        for (BUS in BUSES) {
            echo "[${BCT_RELEASE}] ${ARCH}${USE_SECURITY} ${CASE} - Retrieve Compose File"
            dir ('TAF/utils/scripts/docker') {
                sh "rm -rf docker-compose*"
                sh "sh get-compose-file.sh ${ARCH} ${USE_SECURITY} ${BCT_RELEASE} integration-test"
                sh 'ls *.yml'
            }
            
            // Set deploy_tag by Messagebus
            if ( BUS == 'REDIS' ) {
                deploy_tag = 'deploy-base-service'
            } else {
                deploy_tag = 'mqtt-bus'
            }

            echo "[${BCT_RELEASE}] ${ARCH}${USE_SECURITY} ${CASE} - Deploy EdgeX - ${BUS} Bus"
            def deployLog = sh (
                script: "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                        -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                        --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock \
                        ${TAF_COMMON_IMAGE} --exclude Skipped --include ${deploy_tag} -u deploy.robot \
                        -p default --name ${BUS}-bus-deploy-${BCT_RELEASE}",
                returnStdout: true
            )
            deploySuccess = sh (
                script: "echo '$deployLog' | grep '1 passed'",
                returnStatus: true
            )
            dir ("TAF/testArtifacts/reports/rename-report") {
                sh "cp ../edgex/log.html ${BUS}-bus-deploy-${BCT_RELEASE}-log.html"
                sh "cp ../edgex/report.xml ${BUS}-bus-deploy-${BCT_RELEASE}-report.xml"
            }
            
            if ( deploySuccess == 0 ) {
                echo "[${BCT_RELEASE}] ${ARCH}${USE_SECURITY} ${CASE} - Run Tests Script - ${BUS} Bus"
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} --security-opt label:disable \
                    -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                    -v /tmp/edgex/secrets:/tmp/edgex/secrets:z -v /var/run/docker.sock:/var/run/docker.sock \
                    --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env ${TAF_COMMON_IMAGE} \
                    --exclude Skipped --exclude backward-skip --include MessageQueue=${BUS} \
                    -u integrationTest -p ${profile} --name ${BUS}-bus-test-${BCT_RELEASE}"
                            
                dir ('TAF/testArtifacts/reports/rename-report') {
                    sh "cp ../edgex/log.html ${BUS}-bus-test-${BCT_RELEASE}-log.html"
                    sh "cp ../edgex/report.xml ${BUS}-bus-test-${BCT_RELEASE}-report.xml"
                }
            }
            echo "[${BCT_RELEASE}] ${ARCH}${USE_SECURITY} ${CASE} - Down EdgeX"
            //sh 'curl -X DELETE http://localhost:48080/api/v2/event/age/0'
            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                -e CONF_DIR=/custom-config --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock \
                ${COMPOSE_IMAGE} docker compose -f ${env.WORKSPACE}/TAF/utils/scripts/docker/docker-compose.yml down"
            
                
            echo "[Backward] ${ARCH}${USE_SECURITY} ${CASE} - Generate Backward Compose file"
            dir ('TAF/utils/scripts/docker') {
                sh "rm -rf docker-compose*"
                // Retrieve compose file for core services
                sh "sh get-compose-file.sh ${ARCH} ${USE_SECURITY} ${CORE_SERVICES_RELEASE} integration-test"
                // Retrieve compose file for app-services / device-services
                sh "sh get-compose-file-backward.sh ${ARCH} ${USE_SECURITY} ${COMPATIBLE_RELEASE} ${USE_APP_SERVICE} ${USE_DEVICE_SERVICE}"
                sh "cat docker-compose.yml"
            }
            echo "[Backward] ${ARCH}${USE_SECURITY} ${CASE} - Deploy EdgeX - ${BUS} Bus"
            def deployBackwardLog = sh (
                script: "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                        -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                        --security-opt label:disable -v /var/run/docker.sock:/var/run/docker.sock \
                        ${TAF_COMMON_IMAGE} --exclude Skipped --include ${deploy_tag} -u deploy.robot \
                        -p default --name ${BUS}-bus-deploy-backward",
                returnStdout: true
            )
            deployBacwardSuccess = sh (
                script: "echo '$deployBackwardLog' | grep '1 passed'",
                returnStatus: true
            )
            dir ("TAF/testArtifacts/reports/rename-report") {
                sh "cp ../edgex/log.html ${BUS}-bus-deploy-backward-log.html"
                sh "cp ../edgex/report.xml ${BUS}-bus-deploy-backward-report.xml"
            }
            
            if ( deployBacwardSuccess == 0 ) {
                echo "[Backward] ${ARCH}${USE_SECURITY} ${CASE} - Run Tests Script - ${BUS} Bus"
                sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                    -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} --security-opt label:disable \
                    -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                    -v /tmp/edgex/secrets:/tmp/edgex/secrets:z -v /var/run/docker.sock:/var/run/docker.sock \
                    --env-file ${env.WORKSPACE}/TAF/utils/scripts/docker/common-taf.env \
                    ${TAF_COMMON_IMAGE} --exclude Skipped --exclude backward-skip --include MessageQueue=${BUS} \
                    -u integrationTest -p ${profile} --name ${BUS}-bus-test-backward"
                            
                dir ('TAF/testArtifacts/reports/rename-report') {
                    sh "cp ../edgex/log.html ${BUS}-bus-test-backward-log.html"
                    sh "cp ../edgex/report.xml ${BUS}-bus-test-backward-report.xml"
                }
            }
            
            echo "[Backward] ${ARCH}${USE_SECURITY} ${CASE} - Shutdown EdgeX - ${BUS} Bus"
            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                --exclude Skipped --include shutdown-edgex -u shutdown.robot -p ${profile} --name ${BUS}-bus-shutdown"
        }
        stage ("[Backward] ${ARCH}${USE_SECURITY} ${CASE} - Stash Report") {
            echo '===== Merge Reports ====='
            sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                rebot --inputdir TAF/testArtifacts/reports/rename-report \
                --outputdir TAF/testArtifacts/reports/report"
            dir ("TAF/testArtifacts/reports/report") {
                // Check if the merged-report folder exists
                def mergeExist = sh (
                    script: 'ls ../ | grep merged-report',
                    returnStatus: true
                )
                if (mergeExist != 0) {
                    sh 'mkdir ../merged-report'
                }

                if ( CASE == 'CASE1') {
                    LOG_NAME = "${ARCH}${USE_SECURITY}${CASE}-levski-core-jakarta-app-device"
                } else if ( CASE == 'CASE2') {
                    LOG_NAME = "${ARCH}${USE_SECURITY}${CASE}-ljakarta-core-levski-app-device"
                } else if ( CASE == 'CASE3') {
                    LOG_NAME = "${ARCH}${USE_SECURITY}${CASE}-llevski-core-app-jakarta-device"
                } else if ( CASE == 'CASE4') {
                    LOG_NAME = "${ARCH}${USE_SECURITY}${CASE}-llevski-core-device-jakarta-app"
                }

                //Copy log file to merged-report folder
                sh "cp log.html ../merged-report/${LOG_NAME}-log.html"
                sh "cp result.xml ../merged-report/${LOG_NAME}-report.xml"
            }
            stash name: "${ARCH}${USE_SECURITY}${CASE}-report",
            includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
        }
    }
}

return this

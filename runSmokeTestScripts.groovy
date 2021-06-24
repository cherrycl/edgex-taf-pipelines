def main() {
    def BRANCHES = "${BRANCHLIST}".split(',')
    def USE_SECURITY = '-'
    def runbranchstage = [:]

    for (x in BRANCHES) {
        if ("${SECURITY_SERVICE_NEEDED}" == 'true') {
            USE_SECURITY = '-security-'
        }

        def BRANCH = x

        runbranchstage["SmokeTest ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}"]= {
            node("${NODE}") {
                stage ('Checkout edgex-taf repository') {
                    checkout([$class: 'GitSCM',
                        branches: [[name: "*/${BRANCH}"]],
                        doGenerateSubmoduleConfigurations: false, 
                        extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: '']], 
                        submoduleCfg: [], 
                        userRemoteConfigs: [[url: 'https://github.com/edgexfoundry/edgex-taf.git']]
                        ])
                }

                stage ("Deploy EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    dir ('TAF/utils/scripts/docker') {
                        sh "sh get-compose-file.sh ${USE_DB} ${ARCH} ${USE_SECURITY} pre-release ${params.SHA1}"
                    }

                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -e USE_DB=${USE_DB} --security-opt label:disable \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include deploy-base-service -u deploy.robot -p default"
                }

                stage ("Run Functional Tests Script - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include SmokeTest -u functionalTest/V2-API -p default"
                    
                    dir ('TAF/testArtifacts/reports/rename-report') {
                        sh "cp ../edgex/log.html functional-log.html"
                        sh "cp ../edgex/report.xml functional-report.xml"
                    }
                }

                stage ("Run device-virtual Test Script") {
                    // Run Tests
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            --security-opt label:disable -e COMPOSE_IMAGE=${COMPOSE_IMAGE} -e ARCH=${ARCH} \
                            -e SECURITY_SERVICE_NEEDED=${SECURITY_SERVICE_NEEDED} \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include SmokeTest -u functionalTest/device-service -p device-virtual"
                    
                    dir ('TAF/testArtifacts/reports/rename-report') {
                        sh "cp ../edgex/log.html device-service-log.html"
                        sh "cp ../edgex/report.xml device-service-report.xml"
                    }
                    
                }

                stage ("Stash Report - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    echo '===== Merge Reports ====='
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                                -e COMPOSE_IMAGE=${COMPOSE_IMAGE} ${TAF_COMMON_IMAGE} \
                                rebot --inputdir TAF/testArtifacts/reports/rename-report \
                                --outputdir TAF/testArtifacts/reports/smoke-${BRANCH}-report"

                    dir ("TAF/testArtifacts/reports/smoke-${BRANCH}-report") {
                        // Check if the merged-report folder exists
                        def mergeExist = sh (
                            script: 'ls ../ | grep merged-report',
                            returnStatus: true
                        )
                        if (mergeExist != 0) {
                            sh 'mkdir ../merged-report'
                        }
                        //Copy log file to merged-report folder
                        sh "cp log.html ../merged-report/smoke-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-log.html"
                        sh "cp result.xml ../merged-report/smoke-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-report.xml"
                    }
                    stash name: "smoke-${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}-report", includes: "TAF/testArtifacts/reports/merged-report/*", allowEmpty: true
                }

                stage ("Shutdown EdgeX - ${ARCH}${USE_DB}${USE_SECURITY}${BRANCH}") {
                    sh "docker run --rm --network host -v ${env.WORKSPACE}:${env.WORKSPACE}:z -w ${env.WORKSPACE} \
                            -e COMPOSE_IMAGE=${COMPOSE_IMAGE} --security-opt label:disable \
                            -v /var/run/docker.sock:/var/run/docker.sock ${TAF_COMMON_IMAGE} \
                            --exclude Skipped --include shutdown-edgex -u shutdown.robot -p default"
                }                             
            }
        }
    }
    parallel runbranchstage
}

return this

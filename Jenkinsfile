#!/usr/bin/env groovy
def LOGFILES

call([
    project: 'backward-compatibility-test',
])

def call(config) {
    edgex.bannerMessage "[backward-compatibility-testing] RAW Config: ${config}"

    edgeXGeneric.validate(config)
    edgex.setupNodes(config)

    def _envVarMap = edgeXGeneric.toEnvironment(config)

    pipeline {
        agent { label edgex.mainNode(config) }
        triggers { cron('H 23 * * 6') }
        options {
            timestamps()
        }
        parameters {
            choice(name: 'TEST_ARCH', choices: ['All', 'x86_64', 'arm64'])
            choice(name: 'TEST_CASE', choices: ['All', '1', '2', '3', '4'],
                description: '1: core=main & app/device=jakarta, 2: core=jakarta & app/device=main, 3: core/app=main & device=jakarta, 4: core/device=main & app=jakarta')
        }
        environment {
            // Define test branches and device services
            TAF_COMMON_IMAGE_AMD64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common:latest'
            TAF_COMMON_IMAGE_ARM64 = 'nexus3.edgexfoundry.org:10003/edgex-taf-common-arm64:latest'
            COMPOSE_IMAGE = 'docker:20.10.18'
            SECURITY_SERVICE_NEEDED = false
            BCT_RELEASE = 'jakarta'
        }
        stages {
            stage ('Trigger Test') {
                parallel {
                    stage ('Run Backward Compatibilty Test on amd64') {
                        when { 
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'x86_64' }
                        }
                        environment {
                            ARCH = 'x86_64'
                            NODE = edgex.getNode(config, 'amd64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_AMD64}"
                        }
                        stages {
                            stage('amd64'){
                                stages {
                                    stage('amd64 Case 1') {
                                        when {
                                            expression { params.TEST_CASE == 'All' || params.TEST_CASE == '1' }
                                        }
                                        steps {
                                            script {
                                                backwardTest('main', 'jakarta', 'true', 'true', 'CASE1')
                                            }
                                        }
                                    }
                                    stage('amd64 Case 2') {
                                        when {
                                            expression { params.TEST_CASE == 'All' || params.TEST_CASE == '2' }
                                        }
                                        steps {
                                            script {
                                                backwardTest('jakarta', 'main', 'true', 'true', 'CASE2')
                                            }
                                        }
                                    }
                                    stage('amd64 Case 3') {
                                        when {
                                            expression { params.TEST_CASE == 'All' || params.TEST_CASE == '3' }
                                        }
                                        steps {
                                            script {
                                                backwardTest('main', 'jakarta', 'false', 'true', 'CASE3')
                                            }
                                        }
                                    }
                                    stage('amd64 Case 4') {
                                        when {
                                            expression { params.TEST_CASE == 'All' || params.TEST_CASE == '4' }
                                        }
                                        steps {
                                            script {
                                                backwardTest('main', 'jakarta', 'true', 'false', 'CASE4')
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    stage ('Run Backward Compatibilty Test on arm64') {
                        when {
                            expression { params.TEST_ARCH == 'All' || params.TEST_ARCH == 'arm64' }
                        }
                        environment {
                            ARCH = 'arm64'
                            NODE = edgex.getNode(config, 'arm64')
                            TAF_COMMON_IMAGE = "${TAF_COMMON_IMAGE_ARM64}"
                        }
                        stages {
                            stage('arm64'){
                                stages {
                                    stage('arm64 Case 1') {
                                        when {
                                            expression { params.TEST_CASE == 'All' || params.TEST_CASE == '1' }
                                        }
                                        steps {
                                            script {
                                                backwardTest('main', 'jakarta', 'true', 'true', 'CASE1')
                                            }
                                        }
                                    }
                                    stage('arm64 Case 2') {
                                        when {
                                            expression { params.TEST_CASE == 'All' || params.TEST_CASE == '2' }
                                        }
                                        steps {
                                            script {
                                                backwardTest('jakarta', 'main', 'true', 'true', 'CASE2')
                                            }
                                        }
                                    }
                                    stage('arm64 Case 3') {
                                        when {
                                            expression { params.TEST_CASE == 'All' || params.TEST_CASE == '3' }
                                        }
                                        steps {
                                            script {
                                                backwardTest('main', 'jakarta', 'false', 'true', 'CASE3')
                                            }
                                        }
                                    }
                                    stage('arm64 Case 4') {
                                        when {
                                            expression { params.TEST_CASE == 'All' || params.TEST_CASE == '4' }
                                        }
                                        steps {
                                            script {
                                                backwardTest('main', 'jakarta', 'true', 'false', 'CASE4')
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            stage ('Publish Robotframework Report...') {
                steps{
                    script {
                        //  Test Report
                        if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'x86_64')) {
                            if (("${params.TEST_CASE}" == 'All' || "${params.TEST_CASE}" == '1')) {
                                catchError { unstash "x86_64-CASE1-report" }
                            }
                            if (("${params.TEST_CASE}" == 'All' || "${params.TEST_CASE}" == '2')) {
                                catchError { unstash "x86_64-CASE2-report" }
                            }
                            if (("${params.TEST_CASE}" == 'All' || "${params.TEST_CASE}" == '3')) {
                                catchError { unstash "x86_64-CASE3-report" }
                            }
                            if (("${params.TEST_CASE}" == 'All' || "${params.TEST_CASE}" == '4')) {
                                catchError { unstash "x86_64-CASE4-report" }
                            }
                        }
                        if (("${params.TEST_ARCH}" == 'All' || "${params.TEST_ARCH}" == 'arm64')) {
                            if (("${params.TEST_CASE}" == 'All' || "${params.TEST_CASE}" == '1')) {
                                catchError { unstash "arm64-CASE1-report" }
                            }
                            if (("${params.TEST_CASE}" == 'All' || "${params.TEST_CASE}" == '2')) {
                                catchError { unstash "arm64-CASE2-report" }
                            }
                            if (("${params.TEST_CASE}" == 'All' || "${params.TEST_CASE}" == '3')) {
                                catchError { unstash "arm64-CASE3-report" }
                            }
                            if (("${params.TEST_CASE}" == 'All' || "${params.TEST_CASE}" == '4')) {
                                catchError { unstash "arm64-CASE4-report" }
                            }
                        }
                        

                        dir ('TAF/testArtifacts/reports/merged-report/') {
                            LOGFILES= sh (
                                script: 'ls *-log.html | sed ":a;N;s/\\n/,/g;ta"',
                                returnStdout: true
                            )
                            publishHTML(
                                target: [
                                    allowMissing: false,
                                    alwaysLinkToLastBuild: false,
                                    keepAll: true,
                                    reportDir: '.',
                                    reportFiles: "${LOGFILES}",
                                    reportName: 'Backward Compatibilty Test Reports']
                            )
                        }
                    }

                    junit 'TAF/testArtifacts/reports/merged-report/**.xml'
                }
            }
        }
    }
} 

def backwardTest(CORE_SERVICES_RELEASE, COMPATIBLE_RELEASE, USE_APP_SERVICE, USE_DEVICE_SERVICE, CASE) {
    catchError {
        timeout(time: 50, unit: 'MINUTES') {
            def rootDir = pwd()
            def runBackwardTestScripts = load "${rootDir}/runBackwardTestScripts.groovy"
            runBackwardTestScripts.main(CORE_SERVICES_RELEASE, COMPATIBLE_RELEASE, USE_APP_SERVICE, USE_DEVICE_SERVICE, CASE)
        }
    }      
}

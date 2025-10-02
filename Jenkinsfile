pipeline {
    agent any

    parameters {
        string(name: 'repo_name', defaultValue: '', description: 'Service repository name (from webhook)')
        string(name: 'branch_name', defaultValue: '', description: 'Branch name (from webhook)')
    }

    environment {
        SONAR_TOKEN       = credentials('sonar-token')
        META_REPO_DIR     = "${WORKSPACE}/meta-repo"
        SMTP_CREDENTIALS  = credentials('git-test')
        REGISTRY          = "docker.io"
        REGISTRY_NAMESPACE = "aptusdatalabstech"
    }

    stages {

        stage('Checkout Config Repo') {
            steps {
                dir("${env.META_REPO_DIR}") {
                    git branch: "main", url: "https://github.com/SambitNandaAptus/multi-repo-jenkins.git"
                }
            }
        }

        stage('Determine Service') {
            steps {
                script {
                    def config = readYaml file: "${env.META_REPO_DIR}/services-config.yaml"
                    if (!config.containsKey(params.repo_name)) {
                        error "Repo ${params.repo_name} not configured in services-config.yaml"
                    }
                    def service = config[params.repo_name]
                    if (!service) error "Repo '${params.repo_name}' not found or YAML malformed."

                    env.SERVICE_NAME  = params.repo_name
                    env.REPO_URL      = service.REPO_URL
                    env.DEPLOY_SERVER = service.DEPLOY_SERVER
                }
            }
        }

        stage('Checkout Service Repo') {
            steps {
                script {
                    def branch = params.branch_name.replace('refs/heads/', '')
                    git branch: branch, url: env.REPO_URL, credentialsId: 'git-secret'
                }
            }
        }

        stage('Get Commit Info') {
            steps {
                script {
                    env.COMMIT_SHA = sh(script: "git rev-parse --short HEAD", returnStdout: true).trim()
                    env.COMMIT_AUTHOR_EMAIL = sh(script: "git log -1 --pretty=format:'%ae'", returnStdout: true).trim()
                }
            }
        }

        stage('SonarQube Analysis') {
            when { expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' } }
            steps {
                withSonarQubeEnv('SonarServer') {
                    script {
                        def scannerHome = tool 'sonar-scanner'
                        sh """
                            ${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=${env.SERVICE_NAME}-${env.COMMIT_SHA} \
                            -Dsonar.sources=.
                        """
                    }
                }
            }
        }

       stage('Build Docker Image') {
            when {
        expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' }
    }
            steps {
                script {
                    def imageTag   = "${env.SERVICE_NAME}:${params.branch_name.replaceAll('refs/heads/', '').replaceAll('/', '-')}"
                    def registry   = "docker.io"
                    def scriptPath = "${env.META_REPO_DIR}/scripts/build_and_push.sh"

                    withCredentials([usernamePassword(credentialsId: 'docker-creds',
                                                      usernameVariable: 'DOCKER_USER',
                                                      passwordVariable: 'DOCKER_PASS')]) {
                        sh """
                            chmod +x "${scriptPath}"
                            "${scriptPath}" "${imageTag}" "${registry}" "${DOCKER_USER}" "${DOCKER_PASS}"
                        """
                    }
                }
            }
        }


        stage('Deploy to Dev') {
            when { expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' } }
            steps {
                sshagent(['ssh-deploy-key']) {
                    withCredentials([usernamePassword(credentialsId: 'docker-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        script {
                            def scriptPath = "${env.META_REPO_DIR}/scripts/deploy_compose.sh"
                            def server     = env.DEPLOY_SERVER

                            sh "scp -o StrictHostKeyChecking=no ${scriptPath} aptus@${server}:/tmp/deploy_compose.sh"
                            sh """
                                ssh -o StrictHostKeyChecking=no aptus@${server} '
                                    chmod +x /tmp/deploy_compose.sh
                                    /tmp/deploy_compose.sh "${server}" "${env.REGISTRY}" "${env.REGISTRY_NAMESPACE}/${env.SERVICE_NAME}" "${env.IMAGE_TAG}" "${DOCKER_USER}" "${DOCKER_PASS}"
                                '
                            """
                        }
                    }
                }
            }
        }

        stage('Manual Approval for Staging') {
            when { expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' } }
            steps {
                script {
                    def approvers = "khushi.thacker@aptusdatalabs.com"
                    
                    // Send email notification
                    emailext(
                        to: approvers,
                        subject: "🚦 Approval Needed: Promote ${env.SERVICE_NAME} to Staging",
                        body: """
                            <p>The build for <b>${env.SERVICE_NAME}</b> (commit <code>${env.COMMIT_SHA}</code>) passed in <b>dev</b>.</p>
                            <p><b>Image:</b> ${env.IMAGE_FULL}</p>
                            <p>Click here to approve deployment: <a href="${env.BUILD_URL}input/">Approve</a></p>
                        """,
                        mimeType: 'text/html'
                    )

                    // Pause for approval in Jenkins UI
                    timeout(time: 2, unit: 'HOURS') {
                        input message: "🚀 Approve deployment of ${env.IMAGE_FULL} to STAGING?", ok: "Deploy"
                    }
                }
            }
        }

        // stage('Deploy to Staging') {
        //     when { expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' } }
        //     steps {
        //         sshagent(['ssh-deploy-key']) {
        //             withCredentials([usernamePassword(credentialsId: 'docker-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
        //                 script {
        //                     def stagingServer = "staging-server-ip"
        //                     def scriptPath    = "${env.META_REPO_DIR}/scripts/deploy_compose.sh"

        //                     sh "scp -o StrictHostKeyChecking=no ${scriptPath} aptus@${stagingServer}:/tmp/deploy_compose.sh"
        //                     sh """
        //                         ssh -o StrictHostKeyChecking=no aptus@${stagingServer} '
        //                             chmod +x /tmp/deploy_compose.sh
        //                             /tmp/deploy_compose.sh "${stagingServer}" "${env.REGISTRY}" "${env.REGISTRY_NAMESPACE}/${env.SERVICE_NAME}" "${env.IMAGE_TAG}" "${DOCKER_USER}" "${DOCKER_PASS}"
        //                         '
        //                     """
        //                 }
        //             }
        //         }
        //     }
        // }

    }

    post {
        success {
            script {
                def recipient = env.COMMIT_AUTHOR_EMAIL?.trim() ?: "kthacker862@gmail.com"
                emailext(
                    to: recipient,
                    subject: "✅ Build Success: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: "<p>The commit to <b>${params.repo_name}</b> on branch <b>${params.branch_name}</b> built and deployed successfully!</p><p>Image: ${env.IMAGE_FULL}</p><p><a href='${env.BUILD_URL}'>Build Link</a></p>",
                    mimeType: 'text/html',
                    from: SMTP_CREDENTIALS_USR
                )
            }
        }
        failure {
            script {
                def recipient = env.COMMIT_AUTHOR_EMAIL?.trim() ?: "kthacker862@gmail.com"
                emailext(
                    to: recipient,
                    subject: "❌ Build Failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
                    body: "<p>The commit to <b>${params.repo_name}</b> on branch <b>${params.branch_name}</b> failed the build.</p><p><a href='${env.BUILD_URL}'>Build Link</a></p>",
                    mimeType: 'text/html',
                    from: SMTP_CREDENTIALS_USR
                )
            }
        }
    }
}

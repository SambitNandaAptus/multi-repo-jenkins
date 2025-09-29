pipeline {
    agent any

    parameters {
        string(name: 'repo_name', defaultValue: '', description: 'Service repository name (from webhook)')
        string(name: 'branch_name', defaultValue: '', description: 'Branch name (from webhook)')
    }

    environment {
        SONAR_TOKEN   = credentials('sonar-token')
        META_REPO_DIR = "${WORKSPACE}/meta-repo"
    }

    stages {

        stage('Checkout Config Repo') {
            steps {
                script {
                    echo "Checking out meta repo to read services-config.yaml"
                    dir("${env.META_REPO_DIR}") {
                        git branch: "main", url: "https://github.com/SambitNandaAptus/multi-repo-jenkins.git"
                    }
                }
            }
        }

        stage('Determine Service') {
            steps {
                script {
                    def config = readYaml file: 'services-config.yaml'
                    echo "${params.repo_name}"
                    if (!config.containsKey(params.repo_name)) {
                        error "Repo ${params.repo_name} not configured in services-config.yaml"
                    }
                    def service = config[params.repo_name]
                    if (service == null) {
                        error "Repo '${params.repo_name}' not found or YAML malformed."
                    }

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


        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarServer') {
                    script {
                        def scannerHome = tool 'sonar-scanner'
                        def branchTag   = params.branch_name.replaceAll('refs/heads/', '').replaceAll('/', '-')
                        def projectKey  = "${env.SERVICE_NAME}-${branchTag}"
                        sh """
                            ${scannerHome}/bin/sonar-scanner \
                            -Dsonar.projectKey=${projectKey} \
                            -Dsonar.sources=.
                        """
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                timeout(time: 2, unit: 'MINUTES') {
                    script {
                        def qg = waitForQualityGate(abortPipeline: true)
                        echo "Quality Gate Status: ${qg.status}"
                    }
                }
            }
        }

        stage('Build Docker Image') {
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

       stage('Deploy Service') {
    steps {
        sshagent(['ssh-deploy-key']) {
            withCredentials([usernamePassword(credentialsId: 'docker-creds',
                                              usernameVariable: 'DOCKER_USER',
                                              passwordVariable: 'DOCKER_PASS')]) {
                script {
                    def server     = "192.168.1.235"
                    def registry   = "docker.io"
                    def image      = "aptusdatalabstech/${env.SERVICE_NAME}"
                    def tag        = params.branch_name.replaceAll('refs/heads/', '')
                    def scriptPath = "${env.META_REPO_DIR}/scripts/deploy_compose.sh"

                    if (!fileExists(scriptPath)) {
                        error "Deploy script not found at ${scriptPath}"
                    }

                    echo "[INFO] Copying deploy script to remote server..."
                    sh "scp -o StrictHostKeyChecking=no ${scriptPath} aptus@${server}:/tmp/deploy_compose.sh"

                    echo "[INFO] Running deploy script on remote server..."
                    sh """
                        ssh -o StrictHostKeyChecking=no aptus@${server} '
                            chmod +x /tmp/deploy_compose.sh
                            /tmp/deploy_compose.sh "${server}" "${registry}" "${image}" "${tag}" "${DOCKER_USER}" "${DOCKER_PASS}"
                        '
                    """
                    echo "[INFO] Deployment finished successfully!"
                }
            }
        }
    }
}


    }

    post {
        success {
            echo "Deployment successful for ${env.SERVICE_NAME} on branch ${params.branch_name}"
        }
        failure {
            echo "Deployment FAILED for ${env.SERVICE_NAME} on branch ${params.branch_name}"
        }
    }

}

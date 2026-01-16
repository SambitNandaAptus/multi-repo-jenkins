pipeline {
    agent any
    options {
    skipDefaultCheckout(true)
}

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
        OPENAI_API_KEY = credentials('OPENAI_API_KEY')
        PINECONE_API_KEY  = credentials('PINECONE_API_KEY')
        AWS_ACCESS_KEY_ID = credentials('AWS_ACCESS_KEY_ID')
        AWS_SECRET_ACCESS_KEY = credentials('AWS_SECRET_ACCESS_KEY')
        BUCKET_NAME = credentials('Bucket-name')
        REDIS_HOST = credentials('REDIS_HOST')
        REDIS_PORT = credentials('REDIS_PORT')
        DATABASE_URL = credentials('DATABASE_URL')
        TRAVEL_DATABASE_URI = credentials('TRAVEL_DATABASE_URI')
        LANGSMITH_TRACING = credentials('LANGSMITH_TRACING')
        LANGSMITH_ENDPOINT = credentials('LANGSMITH_ENDPOINT')
        LANGSMITH_API_KEY = credentials('LANGSMITH_API_KEY')
        LANGSMITH_PROJECT = credentials('LANGSMITH_PROJECT')
        GOOGLE_API_KEY = credentials('GOOGLE_API_KEY')
    }

    stages {

        stage('Checkout Config Repo') {
            steps {
                dir("${env.META_REPO_DIR}") {
                    git branch: "main", url: "https://github.com/SambitNandaAptus/multi-repo-jenkins.git"
                }
            }
        }
        stage('Load Notification Scripts') {
    steps {
        script {
            notifications = load "${env.META_REPO_DIR}/scripts/notifications.groovy"
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

                    env.SERVICE_NAME  = params.repo_name.toLowerCase()
                    env.REPO_URL      = service.REPO_URL
                    env.DEPLOY_SERVER = service.DEPLOY_SERVER
                }
            }
        }

        stage('Checkout Service Repo') {
    steps {
        dir("${env.WORKSPACE}") {
            script {
                echo "Cloning from URL: ${env.REPO_URL}"

                def branch = params.branch_name.replace('refs/heads/', '')
                git branch: branch, url: env.REPO_URL, credentialsId: 'git-secret'
                echo "Workspace = ${env.WORKSPACE}"
            }
        }
    }
}
        stage('Branch Guard') {
    steps {
        script {
            def branch = params.branch_name
                .replace('refs/heads/', '')
                .trim()
            if (branch.startsWith('docs/')) {
                currentBuild.result = 'NOT_BUILT'
                error("Skipping CI for docs branch: ${branch}")
            }

            echo "Branch allowed: ${branch}"
        }
    }
}


stage('Debug Service Repo Checkout') {
    steps {
        script {
            echo "Debugging service-repo contents"
            sh """
                echo Current path: \$(pwd)
                ls -lah ${env.WORKSPACE}
                echo "Git status inside service-repo (if any):"
                cd ${env.WORKSPACE} || echo "service-repo dir not found"
                echo "test"
                git status || echo "No git repo present here"
            """
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
        stage('Run Unit Tests in Docker') {
    agent {
        docker {
            image env.SERVICE_NAME == 'pie-ui' ? 'node:18' : 'python:3.10'
            reuseNode true
        }
    }
    steps {
        script {

            if (env.SERVICE_NAME == 'pie-ui') {
                echo "Running JS tests for pie-ui"

                sh """
                    npm install
                    npm test -- --coverage
                """

            }
            else if (env.SERVICE_NAME=="pie-bl") {
                echo "Running Python tests"

                sh """
                     rm -rf node_modules || true
                       rm -rf coverage || true
                      rm -rf .nyc_output || true
                         rm -rf dist || true
                        rm -rf build || true
                      rm -rf reports || true
                    python3 -m venv venv
                    . venv/bin/activate
                    pip install --upgrade "attrs>=23.2.0"
                    pip install pytest pytest-cov
                    mkdir -p reports
                    pip install -r requirements.txt
                    pytest --junitxml=reports/test-results.xml --cov=. --cov-report=term-missing --cov-report=xml:reports/coverage.xml
                """

                junit "reports/test-results.xml"
            }
            else {
                echo "Running Python tests"

                sh """
                     rm -rf node_modules || true
                       rm -rf coverage || true
                      rm -rf .nyc_output || true
                         rm -rf dist || true
                        rm -rf build || true
                      rm -rf reports || true
                    python3 -m venv venv
                    . venv/bin/activate
                    pip install --upgrade pip
                    pip install pytest pytest-cov
                    mkdir -p reports
                    pip install -r requirements.txt
                    pytest app/tests --junitxml=reports/test-results.xml --cov=app --cov-report=xml:reports/coverage.xml
                """

                junit "reports/test-results.xml"
            }
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
                    ls -l reports/coverage.xml || echo 'coverage.xml not found'
                    ${scannerHome}/bin/sonar-scanner
                    rm -rf reports/coverage.xml
                """
            }
        }
    }
}


              stage('Quality Gate') {
            when {
        expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' }
    }
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


              stage('Deploy Service') {
           when {
        expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' }
    }
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
                         rm -rf app
                    """
                    echo "[INFO] Deployment finished successfully!"
                }
            }
        }
    }
}
    //     stage('Notify Dev') {
    // steps {
    //     script {
    //         def recipient = env.COMMIT_AUTHOR_EMAIL?.trim() ?: "kthacker862@gmail.com"
    //         def status = currentBuild.currentResult ?: 'SUCCESS'  // will be SUCCESS, FAILURE, or ABORTED
    //         def subject = "[JENKINS][${status}] Dev Deployment: ${env.SERVICE_NAME} #${env.BUILD_NUMBER}"

    //         try {
    //             emailext(
    //                 to: recipient,
    //                 subject: subject,
    //                 body: """
    //                     <p>Hello,</p>
    //                     <p>The deployment <b>${env.COMMIT_SHA}</b> on branch <b>${params.branch_name}</b> has finished.</p>
    //                     <p>Status: <b>${status}</b></p>
    //                     <p>Build link: <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>
    //                 """,
    //                 mimeType: 'text/html',
    //                 from: SMTP_CREDENTIALS_USR
    //             )
    //         } catch (err) {
    //             echo "Failed to send Dev notification email: ${err}"
    //         }
    //     }
    // }
// }
    }

    post {
    success {
        githubNotify(
          account: 'Amneal-pie',                              
           repo: "${params.repo_name}",                              
            sha: sh(script: "git rev-parse HEAD", returnStdout: true).trim(),
            credentialsId: 'git-secret',                            
           status: 'SUCCESS',                                         
             context: 'CI/CD',
            description: 'Build passed'
         )
        script {
            def branch = params.branch_name.replace('refs/heads/', '')
            if (branch == 'dev') {
                notifications.sendSuccessEmail(
                    env.SERVICE_NAME,
                    params.repo_name
                )
            }
        }
    }
     failure {
        githubNotify(
            account: 'Amneal-pie',
            repo: "${params.repo_name}",
           sha: sh(script: "git rev-parse HEAD", returnStdout: true).trim(),
           credentialsId: 'git-secret',
             status: 'FAILURE',
             context: 'CI/CD',
             description: 'Build failed'
         )
         script {
            notifications.sendFailureEmail(
                env.SERVICE_NAME,
                params.repo_name
            )
        }
         
     }
 }

}


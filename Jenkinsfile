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
stage('Debug Service Repo Checkout') {
    steps {
        script {
            echo "Debugging service-repo contents"
            sh """
                echo Current path: \$(pwd)
                ls -lah ${env.WORKSPACE}
                echo "Git status inside service-repo (if any):"
                cd ${env.WORKSPACE} || echo "service-repo dir not found"
                rm -rf venv
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
       when { expression { return env.SERVICE_NAME != 'pie-ui' } }
  steps {
    
    script {
      sh """
             python3 -m venv venv
    python3 -m venv venv
    . venv/bin/activate 
    pip install --upgrade pip
    pip install pytest pytest-cov
    mkdir -p reports
    pwd
    pip install -r requirements.txt
    pytest app/tests --junitxml=reports/test-results.xml --cov=app --cov-report=xml    
              
      """
      junit "reports/test-results.xml"
      
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
                            -Dsonar.projectKey=${env.SERVICE_NAME} \
                            -Dsonar.sources=. \
                            -Dsonar.python.coverage.reportPaths=coverage.xml
                        """
                    }
                }
            }
        }
    //           stage('Quality Gate') {
    //         when {
    //     expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' }
    // }
    //         steps {
    //             timeout(time: 2, unit: 'MINUTES') {
    //                 script {
    //                     def qg = waitForQualityGate(abortPipeline: true)
    //                     echo "Quality Gate Status: ${qg.status}"
    //                 }
    //             }
    //         }
    //     }


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
                    """
                    echo "[INFO] Deployment finished successfully!"
                }
            }
        }
    }
}
        stage('Notify Dev') {
    steps {
        script {
            def recipient = env.COMMIT_AUTHOR_EMAIL?.trim() ?: "kthacker862@gmail.com"
            def status = currentBuild.currentResult ?: 'SUCCESS'  // will be SUCCESS, FAILURE, or ABORTED
            def subject = "[${status}] Dev Deployment: ${env.SERVICE_NAME} #${env.BUILD_NUMBER}"

            try {
                emailext(
                    to: recipient,
                    subject: subject,
                    body: """
                        <p>Hi,</p>
                        <p>The commit <b>${env.COMMIT_SHA}</b> on branch <b>${params.branch_name}</b> has finished.</p>
                        <p>Status: <b>${status}</b></p>
                        <p>Build link: <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>
                    """,
                    mimeType: 'text/html',
                    from: SMTP_CREDENTIALS_USR
                )
            } catch (err) {
                echo "Failed to send Dev notification email: ${err}"
            }
        }
    }
}




        
        stage('Manual Approval & Deploy to Staging') {
    when { expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' } }
    steps {
        script {
            try {
                def approvers = "khushi.thacker@aptusdatalabs.com,santosh.sahoo@aptusdatalabs.com"
                emailext(
                    to: approvers,
                    from: "khushithacker2003@gmail.com",
                    subject: " Approval Needed: Promote ${env.SERVICE_NAME} to Staging",
                    body: """
                        <p>Hello</p>
                        <p>The build for <b>${env.SERVICE_NAME}</b> (commit <code>${env.COMMIT_SHA}</code>) passed in <b>dev</b>.</p>
                        <p>Build link: <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>
                        <p>Click to approve: <a href='${env.BUILD_URL}input/'>Approve</a></p>
                        <p>The account username : "jenkins-admin"</p>
                        <p>The password: "password"</p>
                    """,
                    mimeType: 'text/html'
                )

                timeout(time: 2, unit: 'HOURS') {
                    input message: " Approve deployment of ${env.IMAGE_FULL} to STAGING?", ok: "Deploy"
                }

                sshagent(['ssh-deploy-key']) {
                    withCredentials([usernamePassword(credentialsId: 'docker-creds', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_PASS')]) {
                        def stagingServer = "192.168.1.235"
                        def scriptPath = "${env.META_REPO_DIR}/scripts/deploy_compose.sh"
                        sh "scp -o StrictHostKeyChecking=no ${scriptPath} aptus@${stagingServer}:/tmp/deploy_compose.sh"
                        sh """
                            ssh -o StrictHostKeyChecking=no aptus@${stagingServer} '
                                chmod +x /tmp/deploy_compose.sh
                                /tmp/deploy_compose.sh "${stagingServer}" "${env.REGISTRY}" "${env.REGISTRY_NAMESPACE}/${env.SERVICE_NAME}" "${env.IMAGE_TAG}" "${DOCKER_USER}" "${DOCKER_PASS}"
                            '
                        """
                    }
                }
                emailext(
                    to: "khushi.thacker@aptusdatalabs.com,${env.COMMIT_AUTHOR_EMAIL}",
                    subject: " Staging Deployment Success: ${env.SERVICE_NAME}",
                    body: "<p>Image has been successfully deployed to STAGING.</p>",
                    mimeType: 'text/html',
                    from: SMTP_CREDENTIALS_USR
                )

            } catch(err) {
                emailext(
                    to: "khushi.thacker@aptusdatalabs.com,${env.COMMIT_AUTHOR_EMAIL}",
                    subject: " Staging Deployment NOT Approved: ${env.SERVICE_NAME}",
                    body: "<p>The deployment to STAGING was aborted or disapproved.</p>",
                    mimeType: 'text/html',
                    from: SMTP_CREDENTIALS_USR
                )
               
                throw err
            }
        }
    }
}
        stage('Notify Production') {
    when {
        expression { return params.branch_name.replaceAll('refs/heads/', '') == 'dev' }
    }
    steps {
        script {
            def recipients = "khushi.thacker@aptusdatalabs.com,${env.COMMIT_AUTHOR_EMAIL ?: 'khushi.thacker@aptusdatalabs.com'}"
            emailext(
                to: recipients,
                subject: "Ready for Production: ${env.SERVICE_NAME}",
                body: """
                    <p>Hi Team,</p>
                    <p>The service <b>${env.SERVICE_NAME}</b> has been successfully deployed to <b>STAGING</b>.</p>
                    <p>Build link: <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>
                    <p>It is now ready for production deployment.</p>
                """,
                mimeType: 'text/html',
                from: env.SMTP_CREDENTIALS_USR
            )
        }
    }
}



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
         emailext(
        to: "${env.COMMIT_AUTHOR_EMAIL}",
        from: "khushithacker2003@gmail.com",
        subject: "Dev Deployment FAILED: ${env.SERVICE_NAME} #${env.BUILD_NUMBER}",
        body: """
          <p>Hi,</p>
          <p>The build <b>#${env.BUILD_NUMBER}</b> failed due to a Quality Gate error.</p>
          <p>Build link: <a href='${env.BUILD_URL}'>${env.BUILD_URL}</a></p>
        """,
        mimeType: 'text/html'
      )
     }
 }

}

// =====================================================================
//  Jenkins Declarative Pipeline
//  CI/CD for a Java (Spring Boot) containerized application.
//  Flow:  Checkout -> Build -> Test -> Build Image -> Push -> Deploy
// =====================================================================
pipeline {
    agent any

    // --- Configuration you adjust for your environment ---
    environment {
        IMAGE_NAME   = 'yourdockerhubuser/java-cicd-demo'   // change to your registry/user
        IMAGE_TAG    = "${BUILD_NUMBER}"                     // unique tag per build
        IMAGE_FULL   = "${IMAGE_NAME}:${IMAGE_TAG}"
        REGISTRY_CRED = 'dockerhub-credentials'             // Jenkins credentials ID
    }

    options {
        timestamps()                       // add timestamps to console log
        timeout(time: 20, unit: 'MINUTES') // fail if the pipeline hangs
    }

    stages {

        stage('Checkout') {
            steps {
                echo 'Cloning the repository...'
                checkout scm
            }
        }

        stage('Build') {
            steps {
                echo 'Compiling and packaging with Maven...'
                sh 'mvn -B clean package -DskipTests'
            }
        }

        stage('Test') {
            steps {
                echo 'Running unit / integration tests...'
                sh 'mvn -B test'
            }
            post {
                always {
                    // Publish test results so Jenkins shows pass/fail trends
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "Building Docker image ${IMAGE_FULL}..."
                sh "docker build -t ${IMAGE_FULL} -t ${IMAGE_NAME}:latest ."
            }
        }

        stage('Push Image') {
            steps {
                echo 'Pushing image to the registry...'
                withCredentials([usernamePassword(
                        credentialsId: "${REGISTRY_CRED}",
                        usernameVariable: 'REG_USER',
                        passwordVariable: 'REG_PASS')]) {
                    sh '''
                        echo "$REG_PASS" | docker login -u "$REG_USER" --password-stdin
                        docker push ${IMAGE_FULL}
                        docker push ${IMAGE_NAME}:latest
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deploying with Docker Compose...'
                // IMAGE env var is consumed by docker-compose.yml
                sh '''
                    export IMAGE=${IMAGE_FULL}
                    docker compose pull
                    docker compose up -d
                '''
            }
        }

        stage('Smoke Test') {
            steps {
                echo 'Verifying the deployed app responds...'
                sh '''
                    sleep 10
                    curl -f http://localhost:8080/health
                '''
            }
        }
    }

    // --- Runs at the end regardless of outcome ---
    post {
        success {
            echo "Pipeline SUCCEEDED. Deployed ${IMAGE_FULL}"
        }
        failure {
            echo 'Pipeline FAILED. Check the stage logs above.'
        }
        always {
            // Free disk space: remove dangling images
            sh 'docker image prune -f || true'
            cleanWs()
        }
    }
}

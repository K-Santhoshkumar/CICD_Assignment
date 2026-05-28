```groovy id="w3jlwm"
pipeline {

    agent any

    environment {

        IMAGE_NAME = 'ksanthoshkumar25/java-cicd-demo'

        IMAGE_TAG = "${BUILD_NUMBER}"

        IMAGE_FULL = "${IMAGE_NAME}:${IMAGE_TAG}"

        REGISTRY_CRED = 'dockerhub-credentials'
    }

    options {

        timestamps()

        timeout(time: 20, unit: 'MINUTES')
    }

    stages {

        stage('Checkout') {

            steps {

                echo 'Cloning repository...'

                checkout scm
            }
        }

        stage('Build') {

            steps {

                echo 'Building Maven project...'

                bat 'mvn -B clean package -DskipTests'
            }
        }

        stage('Test') {

            steps {

                echo 'Running tests...'

                bat 'mvn -B test'
            }

            post {

                always {

                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {

            steps {

                echo 'Building Docker image...'

                bat 'docker build -t %IMAGE_FULL% -t %IMAGE_NAME%:latest .'
            }
        }

        stage('Push Docker Image') {

            steps {

                withCredentials([
                        usernamePassword(
                                credentialsId: 'dockerhub-credentials',
                                usernameVariable: 'REG_USER',
                                passwordVariable: 'REG_PASS'
                        )
                ]) {

                    bat '''
                    docker login -u %REG_USER% -p %REG_PASS%
                    docker push %IMAGE_FULL%
                    docker push %IMAGE_NAME%:latest
                    docker logout
                    '''
                }
            }
        }

        stage('Deploy') {

            steps {

                bat '''
                set IMAGE=%IMAGE_FULL%
                docker compose down
                docker compose up -d
                '''
            }
        }

        stage('Smoke Test') {

            steps {

                bat '''
                timeout /t 10
                curl http://localhost:8080/health
                '''
            }
        }
    }

    post {

        success {

            echo 'Pipeline completed successfully.'
        }

        failure {

            echo 'Pipeline failed.'
        }

        always {

            bat 'docker image prune -f'

            cleanWs()
        }
    }
}
```

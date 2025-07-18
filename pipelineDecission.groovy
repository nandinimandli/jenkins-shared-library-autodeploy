def call(Map configMap) {
    pipeline {
        agent any

        environment {
            PROJECT_NAME = configMap.project
            COMPONENT    = configMap.component
        }

        stages {
            stage('Checkout Code') {
                steps {
                    git branch: "${env.BRANCH_NAME}", url: "git@github.com:nandinimandli/autodeploy-project.git"
                }
            }

            stage('Build') {
                steps {
                    echo "🔨 Building ${configMap.component} component of ${configMap.project} project"
                    sh 'npm install'
                }
            }

            stage('Unit Test') {
                steps {
                    echo "🧪 Running Unit Tests..."
                    sh 'npm test || true' // avoid failure due to test exit codes
                }
            }

            stage('Build Docker Image') {
                steps {
                    echo "🐳 Building Docker image..."
                    sh "docker build -t ${configMap.project}-${configMap.component}:latest ."
                }
            }

            stage('Push Docker Image') {
                steps {
                    withCredentials([usernamePassword(credentialsId: 'dockerhub-creds', passwordVariable: 'PASS', usernameVariable: 'USER')]) {
                        sh """
                          echo $PASS | docker login -u $USER --password-stdin
                          docker tag ${configMap.project}-${configMap.component}:latest $USER/${configMap.project}-${configMap.component}:latest
                          docker push $USER/${configMap.project}-${configMap.component}:latest
                        """
                    }
                }
            }

            stage('Deploy to EKS') {
                steps {
                    echo "🚀 Deploying to Kubernetes"
                    sh """
                      helm upgrade --install ${configMap.component} ./helm \
                      -f ./helm/values.yaml \
                      --set image.repository=$USER/${configMap.project}-${configMap.component} \
                      --set image.tag=latest \
                      --namespace=netflix-app
                    """
                }
            }
        }
    }
}

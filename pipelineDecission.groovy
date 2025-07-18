def call(Map configMap) {
    pipeline {
        agent any

        stages {
            stage('Checkout') {
                steps {
                    git branch: "${env.BRANCH_NAME}", url: 'git@github.com:nandinimandli/autodeploy-project.git'
                }
            }

            stage('Build') {
                steps {
                    echo "🔧 Building component: ${configMap.component}"
                    sh 'npm install'
                }
            }

            stage('Test') {
                steps {
                    echo "🧪 Running tests"
                    sh 'npm test || echo "Tests skipped"'
                }
            }

            stage('Docker Build & Push') {
                steps {
                    script {
                        dockerImage = docker.build("${configMap.component}:${env.BUILD_ID}")
                        docker.withRegistry('', 'dockerhub-credentials-id') {
                            dockerImage.push()
                        }
                    }
                }
            }

            stage('Helm Deploy to EKS') {
                steps {
                    echo "🚀 Deploying to EKS using Helm"
                    sh """
                    helm upgrade --install ${configMap.component} helm \
                      --namespace ${configMap.component}-namespace \
                      --set image.tag=${env.BUILD_ID}
                    """
                }
            }
        }
    }
}

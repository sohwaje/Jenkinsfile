/*[1] 파이프라인에서 진행할 각 stage를 정의하는 파라미터 */
def useTest = true    /* Test 단계 사용 유무 */
def useBuild = true
def useDockerBuild = true
def useDockerPush = true
def useDeploy = true
def usePostcheck = true

/* stage Flow를 확인할 수 있는 설정.
예외 발생 시 기본값으로 동작하도록 try-catch 설정 */
node(''){
stage("Flow Check", {
    try {
        println "  TEST FLOW = $USE_TEST"
        useTest = "$USE_TEST" == "true"
    }
    catch (MissingPropertyException e) {
        println "  TEST FLOW = true"
    }

    try {
        println "  BUILD FLOW = $USE_BUILD"
        useBuild = "$USE_BUILD" == "true"
    }
    catch (MissingPropertyException e) {
        println "  BUILD FLOW = true"
    }

    try {
        println "  DOCKER BUILD = $USE_DOCKERBUILD"
        useDockerBuild = "$USE_DOCKERBUILD" == "true"
    }
    catch (MissingPropertyException e) {
        println "  DOCKER BUILD = true"
    }

    try {
        println "  DOCKER PUSH = $USE_DOCKERPUSH"
        useDockerPush = "$USE_DOCKERPUSH" == "true"
    }
    catch (MissingPropertyException e) {
        println "  DOCKER PUSH = true"
    }

    try {
        println "  DOCKER DEPLOY = $USE_DEPLOY"
        useDeploy = "$USE_DEPLOY" == "true"
    }
    catch (MissingPropertyException e) {
        println "  DOCKER DEPLOY = true"
    }

    try {
        println "  POST CHECK = $POST_CHECK"
        usePostcheck = "$POST_CHECK" == "true"
    }
    catch (MissingPropertyException e) {
        println "  POST CHECK = true"
    }
})

/* 파라미터 검증 단계. 필수 파라미터들은 예외 처리를 하지 않음 */
stage("Parameter Check", {
    println "  GIT_URL = $GIT_URL"
    println "  BRANCH_SELECTOR = $BRANCH_SELECTOR"
    println "  JAVA_VERSION = $JAVA_VERSION"
    println "  ACR_ID = $ACR_ID"
    println "  ACR_PASSWORD = $ACR_PASSWORD"
    println "  ACR_SERVER = $ACR_SERVER"
    env.JAVA_HOME="${tool name : JAVA_VERSION}"
    env.PATH="${env.JAVA_HOME}/bin:${env.PATH}"

})

/* 넘겨 받은 GitUrl과 Branch를 사용하여 Git CheckOut 을 실행.
Test나 Build 중 하나라도 Flow에 포함되어 있다면 이 단계가 실행 */
stage("Git CheckOut", {
    if (useTest || useBuild) {
        println "Git CheckOut Started"
        checkout(
                [
                        $class                           : 'GitSCM',
                        branches                         : [[name: '${BRANCH_SELECTOR}']],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [[url: '${GIT_URL}']]
                ]
        )
        println "Git CheckOut End"
    } else {
        println "Git CheckOut Skip"
    }
})

/* 구체적인 테스트 - 빌드 - 배포 - 실행 파이프라인 */
stage('Test') {
    if (useTest) {
        println "Test Started"
        slackSend (channel: '#hiclass-build-deploy-alert', color: '#FFFF00', message: "TEST START: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh '/opt/maven/apache-maven-3.6.2/bin/mvn test -Pstage'
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "TEST SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        }
        catch (Exception e) {
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#FF0000', message: "TEST ${e}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        } finally {
            junit '**/target/surefire-reports/TEST-*.xml'
        }
            println "Test End"
    } else {
        println "Test Skip"
    }
}

stage('Source Build') {
    if (useBuild) {
        println "Build Started"
        slackSend (channel: '#hiclass-build-deploy-alert', color: '#FFFF00', message: "BUILD START: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh '/opt/maven/apache-maven-3.6.2/bin/mvn install -Pstage'
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "BUILD SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            println "Build End"
        }
        catch (Exception e) {
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "BUILD Error:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        }
    } else {
        println "Build Skip"
    }
}


stage('Docker Build') {
    if (useDockerBuild) {
        println "Docker Build"
        slackSend (channel: '#hiclass-build-deploy-alert', color: '#FFFF00', message: "Docker BUILD START: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh 'docker image build -t ${ACR_SERVER}/hi-class-pro-api:${BUILD_NUMBER} .'
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "Docker BUILD SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            println "Docker Build End"
        }
        catch (Exception e) {
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "Docker BUILD Error:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            }
     } else {
        println "Docker Build Skip"
    }
}


stage('Docker Push') {
    if (useDockerPush) {
        println "Docker Login"
        slackSend (channel: '#hiclass-build-deploy-alert', color: '#FFFF00', message: "Docker PUSH START: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh 'docker login ${ACR_SERVER} -u ${ACR_ID} -p ${ACR_PASSWORD}'
            println "Docker Push"
            sh 'docker push ${ACR_SERVER}/hi-class-pro-api:${BUILD_NUMBER}'
            println "Docker Push End"
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "Docker PUSH SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
    }
        catch (Exception e) {
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "Docker PUSH Error:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        }
    } else {
        println "Docker Push Skip"
    }
}


stage('Deploy') {
    if (useDeploy) {
        println "Replace build number in yml"
        slackSend (channel: '#hiclass-build-deploy-alert', color: '#FFFF00', message: "Deploy START: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh 'sed -i "s/hiclass.azurecr.io\\/hi-class-pro-api.*/hiclass.azurecr.io\\/hi-class-pro-api:${BUILD_NUMBER}/g" hiclass-pro-api.yml'
            println "Replace End"
            sh 'kubectl apply -f hiclass-pro-api.yml --namespace default --kubeconfig /var/lib/jenkins/config_pro'
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "Deploy SUCCESSFUL: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            }
        catch (Exception e) {
            slackSend (channel: '#hiclass-build-deploy-alert', color: '#00FF00', message: "Deploy Error:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            }
    } else {
        println "Docker Deploy Skip"
    }
}
}

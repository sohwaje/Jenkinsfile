def useTest = true
def useBuild = true
def useDockerBuild = true
def useDockerPush = true
def useDeploy = true

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
})

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


stage('Test') {
    if (useTest) {
        println "Test Started"
        /* SLACK Configuration */
        slackSend (channel: '#build_deploy_alert', color: '#FFFF00', message: "Test Start: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
      try {
            sh '/opt/maven/apache-maven-3.6.2/bin/mvn test -Pstage'
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#00FF00', message: "Test Success: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
       } catch (Exception e) {
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#FF0000', message: "Test Failed:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            println "Test End"
            throw e; /* 빌드 중단 */
       } finally {
            junit '**/target/surefire-reports/TEST-*.xml'
        }
    } else {
        println "Test Skip"
    }
}

stage('Source Build') {
    if (useBuild) {
        println "Build Started"
        /* SLACK Configuration */
        slackSend (channel: '#build_deploy_alert', color: '#FFFF00', message: "Build Start: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh '/opt/maven/apache-maven-3.6.2/bin/mvn install -Pstage'
            println "Build End"
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#00FF00', message: "Build Success: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
      }
        catch (Exception e) {
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#FF0000', message: "Build failed:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            throw e;  /* 빌드 중단 */
        }
    } else {
        println "Build Skip"
    }
}

stage('Docker Build') {
    if (useDockerBuild) {
        println "Docker Build"
        /* SLACK Configuration */
        slackSend (channel: '#build_deploy_alert', color: '#FFFF00', message: "Docker Build Start: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh 'docker image build -t ${ACR_SERVER}/hi-class-api:${BUILD_NUMBER} .'
            println "Docker Build End"
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#00FF00', message: "Docker Build Success: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            println "Docker Build End"
      }
        catch (Exception e) {
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#FF0000', message: "Docker Build Error:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            throw e;  /* 빌드 중단 */
        }
    } else {
        println "Docker Build Skip"
    }
}

stage('Docker Push') {
    if (useDockerPush) {
        println "Docker Login"
        /* SLACK Configuration */
        slackSend (channel: '#build_deploy_alert', color: '#FFFF00', message: "Docker Push Start: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh 'docker login ${ACR_SERVER} -u ${ACR_ID} -p ${ACR_PASSWORD}'
            println "Docker Push"
            sh 'docker push ${ACR_SERVER}/hi-class-api:${BUILD_NUMBER}'
            println "Docker Push End"
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#00FF00', message: "Docker Push Success: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
      }
        catch (Exception e) {
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#FF0000', message: "Docker PUSH Error:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            throw e;  /* 빌드 중단 */
        }
    } else {
        println "Docker Push Skip"
    }
}

stage('Deploy') {
    if (useDeploy) {
        println "Replace build number in yml"
        /* SLACK Configuration */
        slackSend (channel: '#build_deploy_alert', color: '#FFFF00', message: "Deploy Start: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
        try {
            sh 'sed -i "s/hiclass.azurecr.io\\/hi-class-api.*/hiclass.azurecr.io\\/hi-class-api:${BUILD_NUMBER}/g" hiclass-stage-api.yml'
            println "Replace End"
            sh 'kubectl apply -f hiclass-stage-api.yml --namespace ingress-basic --kubeconfig /var/lib/jenkins/config'
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#00FF00', message: "Deploy Success: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
      }
        catch (Exception e) {
            /* SLACK Configuration */
            slackSend (channel: '#build_deploy_alert', color: '#FF0000', message: "Deploy Error:${e} Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]' (${env.BUILD_URL})")
            throw e;  /* 빌드 중단 */
        }
    } else {
        println "Docker Deploy Skip"
    }
}
}

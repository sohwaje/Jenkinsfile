/*ref : https://www.youtube.com/watch?v=gdbA3vR2eDs */
/*ref : https://github.com/javahometech/my-app/blob/master/docker-ci-cd-youtube */

node {
	try {
		cleanWs()			/* require install plugin cleanWs: delete workspace */
		gitCheckout()
    srcBuild()
    deploy()
		startapp()
    notifySlack('SUCCESS')
} catch (env) {		/* 예외 발생 시 */
	notifySlack('FAIL')
	throw env
	}
}

/* 깃 체크아웃 */
/* pipeline-syntax -> Snippet Generator -> Steps -> git:Git -> Generate Pipeline Script */
def gitCheckout() {
		stage('SCM Checkout'){												/* Change as needed */
        git credentialsId: '6922e485-806e-41fc-b740-dd9cf76995b0', url: 'https://github.com/sohwaje/todo-app-java-on-azure.git'
    }
	}

/* 빌드 */
/* pipeline-syntax -> Snippet Generator -> tool:Use a tool from a predefined Tool Installtaion -> Tool Type : Maven -> Generate Pipeline Script */
def srcBuild() {
    stage('Maven Build'){
			  notifySlack('Start build')
        def mvnHome = tool name: 'M2_HOME', type: 'maven'	/* mvnHome  변수 설정, maven path name: M2_HOME, type: maven */
        def mvnCMD = "${mvnHome}/bin/mvn"									/* mvn path 설정 */
        sh "echo ${mvnHome}"
        sh "${mvnCMD} clean install -Pdev"							/* Change as needed : clean package, etc */
				if (currentBuild.result == "UNSTABLE") {
					notifySlack("UNSTABLE")
					sh "exit 1"
				}
    }
	}

/* 배포 */
/* Using ssh key, pipeline-syntax -> Snippet Generator -> sshagent: SSH Agent(require plugin install) -> add credentials -> Generate Pipeline Script */
def deploy() {
    stage('Deploy app'){
		notifySlack('Deploy app')
		sshagent(['hiclass-dev-app']) {		/* sshagent target machine name or Jenkins credentials ID(no username)*/
				sh "scp -P 515 -r target/*.jar ubuntu@172.10.10.10:/home/ubuntu/webapps/file"  /* Change as needed */
    }
	}
}

/* 앱실행 */
def startapp() {
		stage('Start app') {
		notifySlack('Start app')
		sshagent(['hiclass-dev-app']) {
			/* Change as needed */
				def startapp = "/home/ubuntu/webapps/bin/file-run.sh start"
				def stopapp = "/home/ubuntu/webapps/bin/file-run.sh stop"
				sh "ssh -p 515 -o StrictHostKeyChecking=no ubuntu@172.10.10.10 ${stopapp}"
				sh "ssh -p 515 -o StrictHostKeyChecking=no ubuntu@172.10.10.10 ${startapp}"
  	 }
	 }
}

/* 슬랙 알림 함수 */
def notifySlack(String buildStatus) {
    def color
    if (buildStatus == 'SUCCESS') {
        color = '#1a9367'
    } else if (buildStatus == 'UNSTABLE') {
        color = '#FFFE89'
		} else if (buildStatus == 'Start build') {
			  color = '#1a9367'
	  } else if (buildStatus == 'Deploy app') {
			  color = '#1a9367'
	  } else if (buildStatus == 'Start app') {
				color = '#1a9367'
    } else {
        color = '#ff0000'
    }
    
/* 슬랙 알림에 나오는 빌드 유저 */
wrap([$class: 'BuildUser']) {
	       // echo "env.BUILD_USER_ID=${env.BUILD_USER_ID}"
				 echo "env.BUILD_USER_FIRST_NAME=${env.BUILD_USER_FIRST_NAME}"

def msg = "${buildStatus}: `${env.JOB_NAME}` #${env.BUILD_NUMBER}: Started by user ${env.BUILD_USER_FIRST_NAME}:\n${env.BUILD_URL}"
slackSend(channel: '#build_deploy_alert', color: color, message: msg)
	}
}

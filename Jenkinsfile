/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
def mavenOptions =' -X'

buildPlugin(
  useContainerAgent: false, // TestContainers
  mavenOptions: mavenOptions,
  configurations: [
    [platform: 'linux', jdk: 21],
    [platform: 'windows', jdk: 17],
])
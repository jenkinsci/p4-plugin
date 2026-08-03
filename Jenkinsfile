/*
 See the documentation for more options:
 https://github.com/jenkins-infra/pipeline-library/
*/
buildPlugin(
  forkCount: '1', // P4 tests are NOT parallel-safe (shared ~/.p4tickets + static ConnectionFactory state); must stay at 1 fork. Do not use 'NC'.
  useContainerAgent: true, // Set to `false` if you need to use Docker for containerized tests
  configurations: [
    [platform: 'linux', jdk: 21],
    [platform: 'windows', jdk: 17],
])

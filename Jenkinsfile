// Builds a module using https://github.com/jenkins-infra/pipeline-library
buildPlugin(
    useContainerAgent: true,
    mavenOptions: '--debug --errors --batch-mode --no-transfer-progress',
    configurations: [
        [platform: 'linux', jdk: 11, env: [MAVEN_OPTS: '-Xmx4224m -Xms512m -XX:+HeapDumpOnOutOfMemoryError']],
        [platform: 'windows', jdk: 11],
        [platform: 'linux', jdk: 17, env: [MAVEN_OPTS: '-Xmx4224m -Xms512m -XX:+HeapDumpOnOutOfMemoryError']]
    ]
)
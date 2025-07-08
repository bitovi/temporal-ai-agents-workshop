curl https://start.spring.io/starter.zip \
  -d dependencies=web,devtools \
  -d type=maven-project \
  -d javaVersion=17 \
  -d bootVersion=3.5.3 \
  -d groupId=com.bitovi.mcp \
  -d artifactId=mcp-server-java \
  -o mcp-server.zip

unzip mcp-server.zip
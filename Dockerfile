FROM maven

WORKDIR /usr/src/app

COPY 1-intro-to-ai-agents/src src
COPY 1-intro-to-ai-agents/pom.xml pom.xml

RUN mvn compile

CMD []

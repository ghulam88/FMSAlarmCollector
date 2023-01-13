FROM java:8
WORKDIR /
ADD AlarmCollector.jar AlarmCollector.jar
EXPOSE 8080
CMD java - jar AlarmCollector.jar
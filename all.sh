#!/usr/bin/env bash

java \
    -javaagent:/var/app/newrelic/newrelic.jar \
    -Dnewrelic.config.agent_enabled="${ENABLE_NEWRELIC:-false}" \
    -Dnewrelic.config.environment=Prod -Dnewrelic.config.app_name=BookProvider \
    -Xms64m -Xmx128m -XX:-TieredCompilation -Xss256k -XX:+UseStringDeduplication -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF8 \
    -jar provider.jar &

java \
    -javaagent:/var/app/newrelic/newrelic.jar \
    -Dnewrelic.config.agent_enabled="${ENABLE_NEWRELIC:-false}" \
    -Dnewrelic.config.environment=Prod -Dnewrelic.config.app_name=TradeGateway \
    -Xms32m -Xmx64m -XX:-TieredCompilation -Xss256k -XX:+UseStringDeduplication -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF8 \
    -jar gateway.jar &

java \
    -javaagent:/var/app/newrelic/newrelic.jar \
    -Dnewrelic.config.agent_enabled="${ENABLE_NEWRELIC:-false}" \
    -Dnewrelic.config.environment=Prod -Dnewrelic.config.app_name=XoOpportunityTrader \
    -Xms128m -Xmx256m -XX:-TieredCompilation -Xss256k -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF8 \
    -jar opportunity-trader.jar &

java \
    -javaagent:/var/app/newrelic/newrelic.jar \
    -Dnewrelic.config.agent_enabled="${ENABLE_NEWRELIC:-false}" \
    -Dnewrelic.config.environment=Prod -Dnewrelic.config.app_name=Persistor \
    -Xms32m -Xmx64m -XX:-TieredCompilation -Xss256k -XX:+UseG1GC -XX:+UseStringDeduplication -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF8 \
    -jar persistor.jar &

wait

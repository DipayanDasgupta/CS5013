JUNIT_URL := https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar
JUNIT_JAR := libs/junit.jar

JACOCO_URL := https://repo1.maven.org/maven2/org/jacoco/org.jacoco.cli/0.8.11/org.jacoco.cli-0.8.11-nodeps.jar
JACOCO_AGENT_URL := https://repo1.maven.org/maven2/org/jacoco/org.jacoco.agent/0.8.11/org.jacoco.agent-0.8.11-runtime.jar
JACOCO_JAR := libs/jacoco-cli.jar
JACOCO_AGENT := libs/jacoco-agent.jar

# --- PIT (mutation testing) --------------------------------------------
PIT_VERSION       := 1.17.4
PIT_JUNIT_VERSION := 1.2.2

PIT_URL       := https://repo1.maven.org/maven2/org/pitest/pitest/$(PIT_VERSION)/pitest-$(PIT_VERSION).jar
PIT_CLI_URL   := https://repo1.maven.org/maven2/org/pitest/pitest-command-line/$(PIT_VERSION)/pitest-command-line-$(PIT_VERSION).jar
PIT_ENTRY_URL := https://repo1.maven.org/maven2/org/pitest/pitest-entry/$(PIT_VERSION)/pitest-entry-$(PIT_VERSION).jar
PIT_J5_URL    := https://repo1.maven.org/maven2/org/pitest/pitest-junit5-plugin/$(PIT_JUNIT_VERSION)/pitest-junit5-plugin-$(PIT_JUNIT_VERSION).jar

PIT_JAR   := libs/pitest.jar
PIT_CLI   := libs/pitest-command-line.jar
PIT_ENTRY := libs/pitest-entry.jar
PIT_J5    := libs/pitest-junit5-plugin.jar

SRCS := $(shell find src -name '*.java' 2>/dev/null)
TESTS := $(shell find test -name '*.java' 2>/dev/null)

.PHONY: deps build test coverage mutation clean

deps: $(JUNIT_JAR)

$(JUNIT_JAR):
	@mkdir -p libs
	@curl -sSL -o $@ $(JUNIT_URL)

$(JACOCO_JAR):
	@mkdir -p libs
	@curl -sSL -o $@ $(JACOCO_URL)

$(JACOCO_AGENT):
	@mkdir -p libs
	@curl -sSL -o $@ $(JACOCO_AGENT_URL)

build: deps
	@mkdir -p build
	javac --release 17 -d build -cp $(JUNIT_JAR) $(SRCS) $(TESTS)

test: build
	java -jar $(JUNIT_JAR) --class-path build --scan-class-path

coverage: build $(JACOCO_JAR) $(JACOCO_AGENT)
	@rm -f jacoco.exec
	java -javaagent:$(JACOCO_AGENT)=destfile=jacoco.exec -jar $(JUNIT_JAR) --class-path build --scan-class-path
	@mkdir -p coverage
	java -jar $(JACOCO_JAR) report jacoco.exec --classfiles build --sourcefiles src --html coverage --xml coverage/report.xml
	@echo "Coverage report: coverage/index.html"

# --- PIT mutation-testing target ---------------------------------------
$(PIT_JAR):
	@mkdir -p libs
	@curl -sSL -o $@ $(PIT_URL)
$(PIT_CLI):
	@mkdir -p libs
	@curl -sSL -o $@ $(PIT_CLI_URL)
$(PIT_ENTRY):
	@mkdir -p libs
	@curl -sSL -o $@ $(PIT_ENTRY_URL)
$(PIT_J5):
	@mkdir -p libs
	@curl -sSL -o $@ $(PIT_J5_URL)

# Classpath: PIT jars + JUnit console launcher (brings the JUnit 5
# platform / engine PIT needs to actually run the tests) + compiled
# code under build/.
PIT_CP := $(PIT_JAR):$(PIT_CLI):$(PIT_ENTRY):$(PIT_J5):$(JUNIT_JAR):build

mutation: build $(PIT_JAR) $(PIT_CLI) $(PIT_ENTRY) $(PIT_J5)
	@mkdir -p build/reports/pitest
	java -cp "$(PIT_CP)" \
	     org.pitest.mutationtest.commandline.MutationCoverageReport \
	     --reportDir=build/reports/pitest \
	     --targetClasses=TaxCalculator \
	     --targetTests=*Test \
	     --sourceDirs=src \
	     --testPlugin=junit5 \
	     --outputFormats=HTML \
	     --timestampedReports=false
	@echo ""
	@echo "PIT report: build/reports/pitest/index.html"

clean:
	rm -rf build libs coverage jacoco.exec

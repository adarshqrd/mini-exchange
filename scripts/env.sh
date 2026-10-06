# Sourced by the other scripts: use JDK 21+ even if Maven defaults to an older JDK.
export JAVA_HOME="${JAVA_HOME_21PLUS:-$(/usr/libexec/java_home -v 21+ 2>/dev/null || echo "$JAVA_HOME")}"
cd "$(dirname "${BASH_SOURCE[0]}")/.."
build() { [[ -d target/classes ]] || mvn -q -B compile; }
run() { "$JAVA_HOME/bin/java" -cp target/classes "$@"; }

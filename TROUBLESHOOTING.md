# Part 2 — Troubleshooting CI/CD Build Pipeline Failure Scenarios

This guide walks through the failures most commonly seen in the pipeline defined in this repository (Jenkins → Maven → Docker → Docker Compose). For each scenario it gives the **symptom** (what you see in the Jenkins console), the **root cause**, and the **fix**. They are grouped by the pipeline stage where they appear.

A useful mental model: a pipeline fails at the **first** stage where an assumption breaks. Read the Jenkins console log from the top and stop at the first red stage — everything after it never ran.

---

## Stage: Checkout

### 1. "Permission denied (publickey)" / repository not found
**Symptom:** The pipeline fails immediately; log shows a Git authentication or "could not read from remote repository" error.
**Cause:** Jenkins has no valid credentials for the repo, or the wrong credentials ID is configured.
**Fix:** Add the correct SSH key or token under *Manage Jenkins → Credentials*, and select it in the job's SCM configuration. For HTTPS repos use a personal access token, not your password.

---

## Stage: Build (Maven)

### 2. Dependencies fail to download
**Symptom:** `Could not transfer artifact ... 403 Forbidden` or `Connection timed out` while downloading from Maven Central.
**Cause:** The build agent has no internet access, is behind a proxy, or a corporate mirror/firewall is blocking the repository. (This is exactly what happens in a locked-down network.)
**Fix:** Configure the proxy/mirror in `~/.m2/settings.xml`, allow-list `repo.maven.apache.org`, or point Maven at an internal Nexus/Artifactory mirror. Verify with `mvn -B dependency:go-offline` on the agent.

### 3. Compilation fails — wrong Java version
**Symptom:** `invalid target release: 17` or `class file has wrong version`.
**Cause:** The agent's JDK is older than the `java.version` declared in `pom.xml`.
**Fix:** Install JDK 17+ on the agent and make it the default, or set the JDK in *Manage Jenkins → Tools* and reference it. Confirm with `java -version` and `mvn -version`.

### 4. "mvn: command not found"
**Symptom:** The Build stage fails instantly with a shell error.
**Cause:** Maven isn't installed on the agent, or isn't on the `PATH` for the Jenkins user.
**Fix:** Install Maven, or configure it under *Manage Jenkins → Tools* and use `withMaven` / `tool` to put it on the path.

---

## Stage: Test

### 5. Tests fail (a real, legitimate failure)
**Symptom:** `Tests run: N, Failures: X` and the build is marked unstable/failed.
**Cause:** A code change broke behavior, or the test itself is wrong. **This is the pipeline doing its job** — do not "fix" it by skipping tests.
**Fix:** Open the JUnit report in Jenkins (published by the `junit` step), read the assertion that failed, and fix the code or the test. Reproduce locally with `mvn test`.

### 6. Flaky test — passes locally, fails in CI
**Symptom:** The same test passes on your machine but intermittently fails in Jenkins.
**Cause:** Timing/port conflicts, shared state, or reliance on wall-clock time. In this project the test boots on a `RANDOM_PORT` precisely to avoid port clashes — flakiness usually comes from elsewhere (e.g. an external call).
**Fix:** Remove order-dependence and sleeps, mock external systems, and make assertions deterministic. Quarantine genuinely flaky tests rather than disabling the whole suite.

### 7. "No tests found" / results not published
**Symptom:** `junit` step warns there are no report files.
**Cause:** Tests didn't run (build failed earlier) or the path `target/surefire-reports/*.xml` is wrong.
**Fix:** Ensure the Test stage actually executed and the surefire path matches your build output.

---

## Stage: Build Docker Image

### 8. "Cannot connect to the Docker daemon"
**Symptom:** `Cannot connect to the Docker daemon at unix:///var/run/docker.sock. Is the docker daemon running?`
**Cause:** The Jenkins user can't access Docker — most common single failure in this whole pipeline.
**Fix:** Add the Jenkins user to the docker group: `sudo usermod -aG docker jenkins`, then restart Jenkins. Confirm with `sudo -u jenkins docker ps`.

### 9. Dockerfile `COPY` fails — file not found
**Symptom:** `COPY failed: stat /app/target/java-cicd-demo.jar: no such file or directory`.
**Cause:** The JAR name in the Dockerfile doesn't match what Maven produced, or the build stage didn't run. In this repo `<finalName>java-cicd-demo</finalName>` in `pom.xml` must match the `COPY` line in the Dockerfile.
**Fix:** Keep the `finalName` and the Dockerfile `COPY` in sync. If you change the artifact name in one place, change it in both.

### 10. Build context too large / slow
**Symptom:** "Sending build context to Docker daemon" transfers hundreds of MB; builds are slow.
**Cause:** `target/`, `.git/`, etc. are being sent into the build.
**Fix:** This repo includes a `.dockerignore` to exclude them. Make sure it's present and lists `target/` and `.git/`.

---

## Stage: Push Image

### 11. "denied: requested access to the resource is denied" / unauthorized
**Symptom:** `docker push` fails with an authorization error.
**Cause:** Not logged in, wrong credentials, or the image name doesn't match your registry namespace.
**Fix:** Verify the `dockerhub-credentials` ID exists in Jenkins and `IMAGE_NAME` starts with your registry username (e.g. `yourname/java-cicd-demo`). The pipeline logs in with `--password-stdin`; check the credential's username/token are current.

### 12. "toomanyrequests" — registry rate limit
**Symptom:** Pulls or pushes fail with a rate-limit message.
**Cause:** Anonymous/free-tier pull limits on the registry.
**Fix:** Authenticate before pulling base images, or mirror base images in a private registry.

---

## Stage: Deploy (Docker Compose)

### 13. "docker compose: command not found"
**Symptom:** Deploy stage errors on the compose command.
**Cause:** Only the legacy standalone `docker-compose` (v1) is installed, or Compose isn't installed at all. This pipeline uses the v2 syntax `docker compose` (a space, not a hyphen).
**Fix:** Install the Docker Compose v2 plugin, or change the commands to `docker-compose` if you must use v1.

### 14. "port is already allocated"
**Symptom:** `Bind for 0.0.0.0:8080 failed: port is already allocated`.
**Cause:** A previous container (or another service) is already using port 8080.
**Fix:** `docker compose down` the old stack first, change the host port mapping in `docker-compose.yml`, or stop whatever owns the port (`docker ps` to find it).

### 15. New image deployed but old code still runs
**Symptom:** Deploy "succeeds" but the app behaves like the previous version.
**Cause:** Compose reused the cached `:latest` image instead of the new tag.
**Fix:** This pipeline tags images with the build number and passes `IMAGE=...:${BUILD_NUMBER}` so each deploy is unique; the `docker compose pull` step fetches it. Always deploy by an immutable tag, not just `latest`.

---

## Stage: Smoke Test

### 16. `curl /health` fails right after deploy
**Symptom:** The Smoke Test stage fails with connection refused, even though Deploy succeeded.
**Cause:** The app container started but the JVM/Spring Boot hadn't finished booting when curl ran.
**Fix:** The pipeline waits with `sleep 10`; for slower apps increase the wait or poll in a loop until `/health` returns 200. The container `HEALTHCHECK` and compose `healthcheck` also help orchestrators wait for readiness.

---

## General debugging checklist

1. **Read top-down.** Find the first red stage; ignore everything after it.
2. **Reproduce locally.** Most Build/Test failures reproduce with `mvn clean package` on your machine.
3. **Run the failing shell command by hand** on the agent as the Jenkins user (`sudo -u jenkins ...`).
4. **Check the basics on the agent:** `java -version`, `mvn -version`, `docker ps`, `docker compose version`.
5. **Permissions and credentials** cause a large share of CI failures — verify the Jenkins user's Docker access and that every credential ID in the Jenkinsfile exists.
6. **Keep names in sync** across `pom.xml` (`finalName`), the Dockerfile (`COPY`), and `docker-compose.yml` (`image`).

# Java CI/CD Pipeline — Containerized Application

A complete, working example of a **CI/CD pipeline that builds, tests, containerizes, and deploys a Java (Spring Boot) application** using **Jenkins** and **Docker Compose**.

This repository is the deliverable for two assignment parts:

1. **Designing a CI/CD pipeline** for deploying a Java-based containerized application.
2. **Troubleshooting CI/CD build pipeline failure scenarios** — see `TROUBLESHOOTING.md`.

---

## 1. What the application is

A minimal Spring Boot REST service with two endpoints:

| Endpoint  | Method | Returns                                  |
|-----------|--------|------------------------------------------|
| `/`       | GET    | A JSON greeting (`?name=` is optional)   |
| `/health` | GET    | `{"status":"UP"}` — used for healthchecks |

The app is intentionally simple so the focus stays on the pipeline.

---

## 2. Repository structure

```
java-cicd-pipeline/
├── src/
│   ├── main/java/com/example/demo/
│   │   ├── DemoApplication.java      # Spring Boot entry point
│   │   └── HelloController.java      # REST endpoints
│   ├── main/resources/
│   │   └── application.properties    # app name + port
│   └── test/java/com/example/demo/
│       └── HelloControllerTest.java  # tests run by the pipeline
├── pom.xml                           # Maven build (produces the JAR)
├── Dockerfile                        # multi-stage build -> small runtime image
├── docker-compose.yml                # deployment definition
├── Jenkinsfile                       # the CI/CD pipeline definition
├── .dockerignore
├── .gitignore
├── README.md
└── TROUBLESHOOTING.md                # Part 2: failure scenarios + fixes
```

---

## 3. The pipeline (what the Jenkinsfile does)

```
 Checkout → Build → Test → Build Image → Push Image → Deploy → Smoke Test
```

| Stage          | What happens                                                        |
|----------------|---------------------------------------------------------------------|
| Checkout       | Pulls the source from Git.                                          |
| Build          | `mvn clean package` compiles and produces the JAR.                  |
| Test           | `mvn test` runs unit/integration tests; results published to Jenkins. |
| Build Image    | `docker build` creates the container image, tagged with the build number. |
| Push Image     | Logs in to the registry and pushes the image.                       |
| Deploy         | `docker compose up -d` deploys the new image on the server.         |
| Smoke Test     | `curl /health` confirms the deployed app is alive.                  |

A failure in any stage stops the pipeline; the `post` block always cleans the workspace and prunes dangling images.

---

## 4. Prerequisites

On your machine / server:

- **JDK 17+** and **Maven 3.8+** (for local builds)
- **Docker** and **Docker Compose v2** (`docker compose`, not the old `docker-compose`)
- **Jenkins** with these plugins: *Pipeline*, *Git*, *Docker Pipeline*, *JUnit*
- A **Docker registry account** (e.g. Docker Hub)

---

## 5. Run it locally first (no Jenkins needed)

This proves the app and container work before automating:

```bash
# 1. Build and test the Java app
mvn clean package

# 2. Run it directly
java -jar target/java-cicd-demo.jar
#    visit http://localhost:8080/  and  http://localhost:8080/health

# 3. Or build & run the container
docker build -t java-cicd-demo:local .
IMAGE=java-cicd-demo:local docker compose up -d
curl http://localhost:8080/health      # -> {"status":"UP"}
docker compose down                    # stop it
```

---

## 6. Set up the Jenkins pipeline

1. **Add registry credentials**: Jenkins → *Manage Jenkins* → *Credentials* → add a *Username with password* credential with ID **`dockerhub-credentials`** (matches `REGISTRY_CRED` in the Jenkinsfile).
2. **Edit the Jenkinsfile**: change `IMAGE_NAME` to your registry user, e.g. `yourname/java-cicd-demo`.
3. **Create the job**: *New Item* → *Pipeline* → under *Pipeline*, choose *Pipeline script from SCM*, point it at your Git repo. Jenkins auto-detects the `Jenkinsfile`.
4. **Ensure the agent can run Docker**: the Jenkins user must be in the `docker` group (`sudo usermod -aG docker jenkins` then restart Jenkins).
5. **Build Now**. Watch the stage view light up.

---

## 7. Notes on deployment target

In this setup Jenkins deploys to the **same host** it runs on (simplest for an assignment). To deploy to a **separate server**, replace the Deploy stage commands with an SSH step, e.g. copy `docker-compose.yml` over and run `docker compose up -d` remotely via `ssh user@server`.

---

## 8. Cleaning up

```bash
docker compose down          # stop the app
docker image prune -f        # remove dangling images
```

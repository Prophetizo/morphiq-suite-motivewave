# Repository Guidelines

## Critical: MotiveWave SDK Docs
**ALWAYS refer to the official MotiveWave SDK documentation:**
   - SDK JavaDoc: https://www.motivewave.com/sdk/javadoc/index.html
   - Example Studies Repository: https://github.com/Prophetizo/motivewave-public-studies
   - Use the JavaDoc to verify:
     - Correct method signatures
     - Available enumerations and constants
     - Proper use of SDK classes
     - Parameter types and return values
   - Use the example studies to understand:
     - Common implementation patterns
     - Best practices for study structure
     - Proper initialization and calculation flows
     - Settings descriptor configurations

## Project Structure & Modules
- Root Maven project (`pom.xml`) with two modules:
  - `morphiq-common/`: shared utilities, logging, and position management under `src/main/java`; tests in `src/test/java`.
  - `morphiq-wavelets/`: MotiveWave studies/strategies and wavelet logic; packaged JARs in `target/`.
- Documentation lives in `docs/` (architecture, guides, references). See `README.md` for a tree overview.

## Build, Test, and Run
- Full build with tests: `mvn clean install`
- Fast build (skip tests): `mvn clean package -DskipTests`
- Build a single module: `cd morphiq-wavelets && mvn package`
- Install into MotiveWave (example): `cp morphiq-wavelets/target/morphiq-wavelets-*-motivewave.jar ~/Documents/MotiveWave/studies/`
- Requirements: Java 23+, Maven 3.9+. Some dependencies resolve from GitHub/GitLab package registries (configure `~/.m2/settings.xml`).

## Coding Style & Naming
- Language: Java 23. Indentation: 4 spaces; UTF-8; Unix line endings.
- Packages: `com.morphiqlabs...` (lowercase). Classes: `PascalCase`. Methods/fields: `camelCase`. Constants: `UPPER_SNAKE_CASE`.
- Tests: mirror package structure; class names end with `Test` (unit) or `IntegrationTest` (slower flows).
- Logging: SLF4J (`LoggerFactory.getLogger(...)`); avoid `System.out`.
- Public APIs: include brief Javadoc; prefer clear, immutable data where practical.

## Testing Guidelines
- Frameworks: JUnit 5 (Jupiter) and Mockito. Run tests with `mvn test` or at module level.
- Focus: deterministic numeric checks for wavelet ops; unit tests for position sizing/tracking in `morphiq-common`.
- Coverage: prioritize critical paths (signal generation, order handling). Add tests alongside code changes.

## Commit & Pull Request Guidelines
- Commits: imperative, concise, scoped (e.g., "Fix VectorWave API migration", "Feat: Wavelet ATR"). Reference issues/PRs like `(#31)`.
- PRs: include summary, motivation, key changes, and testing notes. Link related issues; attach logs or screenshots when relevant.
- CI: ensure `mvn -q -e -DskipTests=false package` passes locally. For UI verification, attach steps to reproduce in MotiveWave and the JAR path used.

## Security & Configuration
- Private registries: add credentials for `github`, `gitlab-maven`, and `github-morphiq-vectorwave` in `~/.m2/settings.xml` (`<server><id>…</id></server>` entries).
- Do not commit secrets or local MotiveWave paths; use environment or user-level Maven settings.

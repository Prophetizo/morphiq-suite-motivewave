# Morphiq Suite MotiveWave Development Instructions

**CRITICAL**: Always follow these instructions first and only fallback to additional search and context gathering if the information in the instructions is incomplete or found to be in error.

## Project Overview

Morphiq Suite MotiveWave is a multi-module Maven project providing advanced wavelet-based trading indicators for the MotiveWave platform. It uses Java 21+ and leverages parallel processing for high-performance signal analysis.

**⚠️ CRITICAL LIMITATION**: This project requires external authentication to build due to dependencies on:
- VectorWave library (private GitHub/GitLab packages)
- MotiveWave SDK (provided scope)

**Without proper credentials, ALL build and test commands will fail with dependency resolution errors.**

## Working Effectively

### Prerequisites and Setup
- Install Java 21+ (required - the project enforces Java 21 minimum):
  ```bash
  sudo apt update && sudo apt install -y openjdk-21-jdk
  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
  export PATH=$JAVA_HOME/bin:$PATH
  ```
- Maven 3.6+ is required (usually pre-installed)

### Initial Setup and Build Process

**CRITICAL BUILD REQUIREMENTS**:
- The project depends on external libraries (VectorWave, MotiveWave SDK) that require authentication
- Build will FAIL without proper repository credentials (GITLAB_TOKEN, GITHUB_TOKEN)
- **NEVER CANCEL** builds - they may take 30+ minutes to complete
- **Always set timeout to 60+ minutes** for build commands

#### Build Commands

```bash
# Full clean build with dependencies update - NEVER CANCEL, takes 20-30 minutes
mvn clean package -U
# Set timeout: 1800+ seconds (30+ minutes)

# Quick build without tests - NEVER CANCEL, takes 15-20 minutes  
mvn clean package -DskipTests
# Set timeout: 1200+ seconds (20+ minutes)

# Individual module build (faster if dependencies are resolved)
cd morphiq-core && mvn clean package -DskipTests
# Set timeout: 600+ seconds (10+ minutes)

# Install to local repository - NEVER CANCEL, takes 25-35 minutes
mvn clean install
# Set timeout: 2100+ seconds (35+ minutes)
```

#### Test Commands

```bash
# Run all tests - NEVER CANCEL, takes 10-15 minutes
mvn test
# Set timeout: 900+ seconds (15+ minutes)

# Run specific test class
mvn test -Dtest=StatisticsTest
# Set timeout: 300+ seconds (5+ minutes)

# Run tests in specific module
cd morphiq-core && mvn test
# Set timeout: 600+ seconds (10+ minutes)
```

### Repository Authentication Setup

The build requires access to external Maven repositories. If credentials are not available:

1. **Expected Behavior**: Build will fail with dependency resolution errors for `ai.prophetizo:vector-wave`
2. **GitLab Maven Repository**: `https://gitlab.com/api/v4/projects/63189175/packages/maven` (requires GITLAB_TOKEN)
3. **GitHub Packages**: `https://maven.pkg.github.com/prophetizo/vectorwave` (requires GITHUB_TOKEN)

**DO NOT** attempt to build without proper credentials - focus on code analysis and documentation instead.

### Known Build Issues and Workarounds

1. **Java Version Mismatch**: 
   - Project requires Java 21+ but may show Java 23 in some POMs
   - Always use Java 21 - it works correctly
   - Ignore warnings about Java version differences

2. **Maven Unsafe Warnings**:
   - Expected warnings from Maven's internal Guava dependency
   - Filter out with: `mvn package 2>&1 | grep -v "WARNING.*Unsafe"`
   - These are harmless and documented in `BUILD_NOTES.md`

3. **External Dependency Failures**:
   - VectorWave dependency will fail without authentication
   - MotiveWave SDK is provided scope (not needed for compilation in CI)
   - Document this limitation but don't try to work around it

## Module Structure and Navigation

### Key Modules
- **morphiq-core/**: Core wavelet processing library and mathematical utilities
  - Location: `/morphiq-core/src/main/java/com/prophetizo/`
  - Key classes: `Statistics.java`, `MovingAverage.java`, `WaveletAnalyzer.java`
  - Tests: `/morphiq-core/src/test/java/com/prophetizo/`

- **morphiq-common/**: MotiveWave-specific utilities  
  - Location: `/morphiq-common/src/main/java/com/prophetizo/`
  - Shared utilities for MotiveWave integration

- **morphiq-autowave/**: Automatic wavelet decomposition indicator
  - Location: `/morphiq-autowave/src/main/java/com/prophetizo/studies/`
  - Main study: `AutoWavelets.java`

- **morphiq-denoise/**: Denoised trend following indicator
  - Location: `/morphiq-denoise/src/main/java/com/prophetizo/studies/`
  - Main study: `DenoisedTrendFollowing.java`

- **morphiq-bundle-premium/**: Packaging module (creates fat JARs)

### Important Configuration Files
- **Parent POM**: `/pom.xml` - manages all dependencies and plugin versions
- **JVM Configuration**: `/.mvn/jvm.config` - Java module system settings
- **Build Notes**: `/BUILD_NOTES.md` - documents known build issues
- **CI Pipeline**: `/.github/workflows/maven.yml` - two-stage build and test
- **Documentation**: `/docs/` - comprehensive project documentation

## Validation Scenarios

### After Making Changes
**IMPORTANT**: Due to external dependency requirements, most build/test commands will fail without proper authentication.

When external dependencies are NOT available:
1. **Code Analysis Only**: Review code changes manually
2. **Documentation Updates**: Focus on documentation and configuration changes  
3. **Mathematical Function Validation**: Test logic manually with small datasets

When external dependencies ARE available:
1. **Basic Compilation Check**:
   ```bash
   mvn compile -DskipTests
   # Set timeout: 600+ seconds
   ```

2. **Unit Tests for Mathematical Functions**:
   ```bash
   mvn test -Dtest=StatisticsTest
   mvn test -Dtest=MovingAverageTest
   # Each test: Set timeout: 300+ seconds
   ```

3. **Code Quality** (if linters available):
   ```bash
   mvn spotbugs:check  # Static analysis (if configured)
   mvn checkstyle:check  # Style checking (if configured)
   ```

### Manual Testing Scenarios
- **Mathematical Functions**: Test `Statistics` and `MovingAverage` classes with edge cases
- **Wavelet Processing**: Verify wavelet transforms with known input/output pairs
- **MotiveWave Integration**: Check study descriptors and indicator configuration
- **Performance**: Monitor parallel processing activation for datasets ≥ 512 points

## Development Workflow

### Common Development Tasks

1. **Adding New Mathematical Functions**:
   - Implement in `/morphiq-core/src/main/java/com/prophetizo/timeseries/math/`
   - Add comprehensive unit tests in corresponding test directory
   - Follow existing patterns in `Statistics.java` and `MovingAverage.java`

2. **Creating New MotiveWave Studies**:
   - Extend `com.motivewave.platform.sdk.study.Study`
   - Implement in appropriate module (autowave, denoise, etc.)
   - Use `StudyDescriptor` for configuration
   - Override `calculate()` method for main logic

3. **Modifying Wavelet Processing**:
   - Core processing logic in `/morphiq-core/src/main/java/com/prophetizo/wavelets/`
   - Supports Daubechies4, Daubechies6 wavelets  
   - Automatic parallel processing for performance
   - Handles non-power-of-two data lengths

### Architecture Patterns
1. **Parallel Processing**: Automatic parallelization for datasets ≥ 512 points
2. **MotiveWave Integration**: All indicators extend `com.motivewave.platform.sdk.study.Study`
3. **Maven Shade Plugin**: Creates fat JARs with all dependencies included
4. **Modular Architecture**: Each indicator is independently deployable

## Frequently Referenced Information

### Module Dependencies
```
morphiq-bundle-premium (pom)
├── morphiq-autowave (jar)
│   ├── morphiq-common (jar)
│   │   └── morphiq-core (jar)
│   │       └── vector-wave (external)
│   └── mwave_sdk (provided)
└── morphiq-denoise (jar)
    ├── morphiq-common (jar)
    └── mwave_sdk (provided)
```

### Key External Dependencies
- **VectorWave** (v1.0-SNAPSHOT): High-performance wavelet transforms
- **MotiveWave SDK** (v20230627): Platform integration (provided scope)
- **SLF4J Simple** (v2.0.17): Logging
- **JUnit 5** & **Mockito**: Testing framework

### Build Artifacts
After successful build, artifacts are located at:
- Individual modules: `{module}/target/{module}-1.0.0-SNAPSHOT.jar`
- Premium bundle: `morphiq-bundle-premium/target/morphiq-premium-*.jar`
- For MotiveWave: Copy bundle JAR to `~/Documents/MotiveWave/studies/`

## CI/CD Information

The GitHub Actions workflow (`.github/workflows/maven.yml`):
- **Two-stage pipeline**: build and test (runs separately)
- **Java 23 in CI** (but Java 21+ works locally)
- **Authentication required**: GITLAB_TOKEN and GITHUB_TOKEN secrets
- **Artifact upload**: Individual modules and premium bundle
- **Expected build time**: 15-25 minutes total

## Troubleshooting

### "Cannot resolve dependency" errors
- **Cause**: Missing authentication for external repositories (VectorWave, MotiveWave SDK)
- **Impact**: Prevents all compilation and testing
- **Solution**: Document the limitation, request credentials, or focus on non-compilation tasks
- **Workaround for development**: Analyze code manually, review documentation, plan architecture changes

### "Unsafe" warnings during build
- **Cause**: Maven's internal Guava dependency (documented in BUILD_NOTES.md)
- **Solution**: Harmless, can be filtered with grep
- **Status**: Will resolve when Maven updates Guava

### Java version conflicts
- **Cause**: POM shows Java 23 requirement but Java 21 works
- **Solution**: Use Java 21, ignore version mismatch warnings
- **Update**: Modify maven.compiler.release to 21 if needed

### Long build times
- **Cause**: Complex shading, external dependency resolution, parallel processing setup
- **Expected**: 15-30+ minutes for full builds
- **Critical**: NEVER CANCEL builds - they will complete given sufficient time

## Quick Reference

| Command | Purpose | Timeout | Notes |
|---------|---------|---------|-------|
| `mvn clean package` | Full build | 30+ min | NEVER CANCEL |
| `mvn clean package -DskipTests` | Build without tests | 20+ min | NEVER CANCEL |
| `mvn test` | Run all tests | 15+ min | NEVER CANCEL |
| `mvn test -Dtest=ClassName` | Single test | 5+ min | Safe to use |
| `mvn compile` | Compile only | 10+ min | Quick validation |

**Remember**: This project requires external authentication for full builds. Focus on code analysis, documentation, and local mathematical function testing when credentials are not available.
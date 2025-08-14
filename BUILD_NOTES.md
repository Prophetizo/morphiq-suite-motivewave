# Build Notes

## Known Issues

### Unsafe Warning from Maven's Guava Dependency

When building with Java 23+ and Maven 3.9.x, you may see warnings like:
```
WARNING: A terminally deprecated method in sun.misc.Unsafe has been called
WARNING: sun.misc.Unsafe::objectFieldOffset has been called by com.google.common.util.concurrent.AbstractFuture$UnsafeAtomicHelper
```

This warning comes from Maven's internal Guava dependency (not from the project itself) and is harmless. The warning will be resolved when Maven updates to a newer version of Guava that doesn't use deprecated Unsafe methods.

### Workaround

To suppress these warnings during builds, you can use:
```bash
mvn package 2>&1 | grep -v "WARNING.*Unsafe"
```

Or create an alias:
```bash
alias mvnq='mvn 2>&1 | grep -v "WARNING.*Unsafe"'
```

## Successfully Resolved Issues

1. **Maven Shade Plugin Warnings** - Fixed by removing the minimizeJar configuration and letting the shade plugin handle overlapping classes naturally.
# Transfer Performance Improvements

## Summary of Changes

This document outlines all improvements made to the TCP and HTTP transfer mechanisms for GlanceMap.

---

## Problem Analysis

### 1. HTTP Transfer Stuck on "Connecting to Phone (HTTP)"
**Root Cause**: The watch-side `HttpTransferStrategy` was waiting for a Wi-Fi network callback that never completed when Wi-Fi was already active. The `requestWifiNetwork()` callback only triggered `onAvailable()` when a *new* network became available, not when Wi-Fi was already connected.

**Fix**: Added a check to see if Wi-Fi is already available before requesting the network callback.

### 2. TCP Socket Speed Limited to 3-4 MB/s
**Root Causes**:
- Buffer sizes were too small (1MB)
- Progress callbacks were too frequent (every 500ms / 1MB), causing I/O overhead
- Socket options weren't fully optimized
- Missing keepAlive for long transfers

---

## Detailed Changes

### 1. **HttpTransferStrategy.kt (Watch Side)**
**File**: `app/src/main/java/.../transfer/HttpTransferStrategy.kt`

**Changes**:
- ✅ **Check for existing Wi-Fi** before requesting network
  ```kotlin
  var wifiNetwork = cm.activeNetwork?.let { network ->
      val caps = cm.getNetworkCapabilities(network)
      if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
          Log.d(TAG, "✅ Wi-Fi already active")
          network
      } else null
  }
  ```
- ✅ Increased buffer size from 1MB → 2MB
- ✅ Increased connect timeout from 10s → 15s
- ✅ Increased read timeout from 5min → 10min for large files
- ✅ Added better logging for debugging

### 2. **TcpSocketStrategy.kt (Watch Side)**
**File**: `app/src/main/java/.../transfer/TcpSocketStrategy.kt`

**Changes**:
- ✅ **Check for existing Wi-Fi** before requesting network (same fix as HTTP)
- ✅ Increased buffer size from 1MB → 2MB
- ✅ Set buffer sizes **before** connecting (more effective)
- ✅ Added `keepAlive = true` for long transfers
- ✅ Changed socket performance preferences to prioritize bandwidth:
  ```kotlin
  s.setPerformancePreferences(0, 0, 1) // connectionTime=0, latency=0, bandwidth=1
  ```
- ✅ Increased timeouts for large files (10 minutes)
- ✅ Enhanced logging

### 3. **TcpSocketStrategy.kt (Companion App)**
**File**: `glancemapcompanionapp/src/main/java/.../TcpSocketStrategy.kt`

**Changes**:
- ✅ Increased buffer size from 1MB → 2MB
- ✅ Set server socket receive buffer size
- ✅ Added `keepAlive = true` on client socket
- ✅ Optimized socket performance preferences for bandwidth
- ✅ Set buffer sizes on both send and receive
- ✅ Enhanced logging with buffer size information

### 4. **HttpTransferServer.kt (Companion App)**
**File**: `glancemapcompanionapp/src/main/java/.../HttpTransferServer.kt`

**Changes**:
- ✅ Increased stream buffer from implicit default → 2MB
- ✅ Added explicit Content-Length header
- ✅ Manual buffer copying with 2MB chunks for better control
- ✅ Added port assignment timeout check (prevents silent failures)
- ✅ Enhanced logging throughout request lifecycle
- ✅ Better error handling for server startup

### 5. **TransferUtils.kt (Both Sides)**
**Files**: 
- `app/src/main/java/.../TransferUtils.kt`
- `glancemapcompanionapp/src/main/java/.../TransferUtils.kt`

**Changes**:
- ✅ Increased buffer size constant from 1MB → 2MB
- ✅ Reduced progress callback frequency:
  - Update interval: 500ms → 1000ms
  - Update byte threshold: 1MB → 2MB
- ✅ This reduces I/O interruptions and improves throughput

---

## Expected Performance Improvements

### TCP Socket Transfer
**Before**: 3-4 MB/s
**Expected After**: 6-10 MB/s (potentially higher on good Wi-Fi)

**Improvements from**:
- 2x larger buffers (less system calls)
- 2x less frequent progress updates (less overhead)
- Socket optimization for bandwidth
- keepAlive for stable long transfers

### HTTP Transfer
**Before**: Not working (stuck on "Connecting...")
**Expected After**: Working, similar speed to TCP (6-10 MB/s)

**Improvements from**:
- Fixed Wi-Fi network detection
- 2x larger buffers
- Proper streaming with chunked writes
- Better error handling and timeouts

---

## Testing Recommendations

### 1. Test HTTP Transfer
1. Select a large file (>100MB)
2. Choose HTTP transfer method
3. **Expected**: Should connect within 5 seconds and start transferring
4. **Watch logs for**: "✅ Wi-Fi already active" or "Requesting Wi-Fi network..."

### 2. Test TCP Transfer Speed
1. Use a very large file (200MB+)
2. Monitor transfer speed in notification
3. **Expected**: Should see 6-10 MB/s (or higher on good networks)
4. **Compare**: Old speed vs new speed

### 3. Test Different Scenarios
- ✅ Phone and watch on same Wi-Fi network
- ✅ Watch connected to phone's hotspot
- ✅ Large .map files (200MB+)
- ✅ Large .gpx files (50MB+)
- ✅ Multiple transfers in sequence

---

## Logging for Debugging

### Key log messages to watch:

**Watch Side (HTTP)**:
```
✅ Wi-Fi already active
🌐 HTTP GET http://...
HTTP Response: 200 OK
Content-Length: XXX bytes
✅ HTTP receive done
```

**Watch Side (TCP)**:
```
✅ Wi-Fi already active
Connecting to 192.168.x.x:XXXX
Socket buffer sizes: rcv=2097152, snd=2097152
✅ Connected! Receiving stream...
✅ TCP receive completed successfully
```

**Phone Side (TCP)**:
```
TCP Server listening on 0.0.0.0:XXXX
✅ Watch connected from /192.168.x.x:XXXX
Socket buffer sizes: snd=2097152, rcv=2097152
✅ Transfer complete
```

**Phone Side (HTTP)**:
```
HTTP Server started at http://192.168.x.x:XXXX
📥 Incoming HTTP request from 192.168.x.x
✅ HTTP stream completed
```

---

## Additional Optimizations to Consider

If you still want even faster transfers, consider:

1. **Increase buffer size to 4MB** for very large files (but test memory impact)
2. **Reduce progress updates even more** (every 2 seconds / 5MB)
3. **Use multiple threads** for reading/writing (complex, may not help much)
4. **Compression** for compressible files (GPX benefits, MAP files likely already compressed)
5. **Parallel TCP streams** (very complex, limited benefit on Wi-Fi)

---

## Notes

- The theoretical maximum on Wi-Fi 5 (802.11ac) is ~50-100 MB/s
- Real-world on Android with overhead: 10-30 MB/s is realistic
- Phone hotspot is typically slower than infrastructure Wi-Fi
- Large buffers require more memory but reduce system calls
- Current 2MB buffers are a good balance for Wear OS devices

---

## Rollback Instructions

If you need to revert these changes:
1. Change all `2 * 1024 * 1024` back to `1024 * 1024`
2. Remove the "check for existing Wi-Fi" blocks
3. Change progress update intervals back to 500ms / 1MB
4. Remove `keepAlive = true` lines
5. Revert socket performance preferences

---

**Last Updated**: 2025
**Author**: Senior Android Developer

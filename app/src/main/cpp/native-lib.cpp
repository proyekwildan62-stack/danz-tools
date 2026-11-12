#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <sys/uio.h>
#include <cmath>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <atomic>

namespace off {
    constexpr uintptr_t kMatchGame           = 0x7A94B20;
    constexpr uintptr_t kLocalPlayer         = 0x48;
    constexpr uintptr_t kDictionaryEntities  = 0x60;
    constexpr uintptr_t kDictEntries         = 0x18;
    constexpr uintptr_t kDictCount           = 0x20;

    constexpr uintptr_t kIsDead              = 0x4C;
    constexpr uintptr_t kTeamId              = 0x58;
    constexpr uintptr_t kHeadTransformNode   = 0x2C8;
    constexpr uintptr_t kHipTransformNode    = 0x2D0;
    constexpr uintptr_t kWeaponDataPtr       = 0x370;
    constexpr uintptr_t kViewAngles          = 0x410;
    constexpr uintptr_t kIsFiring            = 0x438;

    constexpr uintptr_t kWeaponRecoilUp      = 0x84;
    constexpr uintptr_t kWeaponScatter       = 0x8C;
    constexpr uintptr_t kTouchSensMultiplier = 0x7B18440;
}

struct Vec3 { float x, y, z; };
struct Vec2 { float x, y; };

struct EngineState {
    std::atomic<bool>  running{false};
    std::atomic<bool>  dragHs{false};
    std::atomic<bool>  aimDrag{false};
    std::atomic<bool>  aimSmooth{false};
    std::atomic<bool>  controlRecoil{false};
    std::atomic<bool>  danzWareCore{false};

    std::atomic<float> dragMultiplier{1.45f};
    std::atomic<float> smoothFactor{3.5f};
    std::atomic<float> recoilKeepRatio{0.15f};
    std::atomic<float> danzFovDeg{12.0f};

    pid_t              targetPid{-1};
    uintptr_t          il2cppBase{0};
    char               pkgName[128]{"com.dts.freefireth"};
};

static EngineState gState;
static pthread_t   gWorkerThread;

template <typename T>
[[nodiscard]] bool ReadProcMem(pid_t pid, uintptr_t addr, T& out) noexcept {
    if (pid <= 0 || addr < 0x10000 || addr > 0x7FFFFFFFFFFFULL) return false;
    struct iovec local{&out, sizeof(T)};
    struct iovec remote{reinterpret_cast<void*>(addr), sizeof(T)};
    return process_vm_readv(pid, &local, 1, &remote, 1, 0) == static_cast<ssize_t>(sizeof(T));
}

template <typename T>
[[nodiscard]] bool WriteProcMem(pid_t pid, uintptr_t addr, const T& val) noexcept {
    if (pid <= 0 || addr < 0x10000 || addr > 0x7FFFFFFFFFFFULL) return false;
    struct iovec local{const_cast<T*>(&val), sizeof(T)};
    struct iovec remote{reinterpret_cast<void*>(addr), sizeof(T)};
    return process_vm_writev(pid, &local, 1, &remote, 1, 0) == static_cast<ssize_t>(sizeof(T));
}

[[nodiscard]] pid_t FindPidByPackage(const char* pkg) noexcept {
    DIR* dir = opendir("/proc");
    if (!dir) return -1;
    struct dirent* entry = nullptr;
    pid_t foundPid = -1;
    while ((entry = readdir(dir)) != nullptr) {
        int id = std::atoi(entry->d_name);
        if (id <= 0) continue;
        char cmdPath[64];
        std::snprintf(cmdPath, sizeof(cmdPath), "/proc/%d/cmdline", id);
        int fd = open(cmdPath, O_RDONLY);
        if (fd < 0) continue;
        char cmdLine[128]{};
        ssize_t len = read(fd, cmdLine, sizeof(cmdLine) - 1);
        close(fd);
        if (len > 0 && std::strstr(cmdLine, pkg) != nullptr) {
            foundPid = static_cast<pid_t>(id);
            break;
        }
    }
    closedir(dir);
    return foundPid;
}

[[nodiscard]] uintptr_t FindLibraryBase(pid_t pid, const char* libName) noexcept {
    char mapPath[64];
    std::snprintf(mapPath, sizeof(mapPath), "/proc/%d/maps", pid);
    FILE* fp = std::fopen(mapPath, "r");
    if (!fp) return 0;
    char line[512];
    uintptr_t base = 0;
    while (std::fgets(line, sizeof(line), fp)) {
        if (std::strstr(line, libName)) {
            base = static_cast<uintptr_t>(std::strtoull(line, nullptr, 16));
            break;
        }
    }
    std::fclose(fp);
    return base;
}

[[nodiscard]] bool ReadTransformPos(pid_t pid, uintptr_t node, Vec3& outPos) noexcept {
    uintptr_t internalPtr = 0;
    if (!ReadProcMem(pid, node + 0x10, internalPtr) || !internalPtr) return false;
    return ReadProcMem(pid, internalPtr + 0x90, outPos);
}

[[nodiscard]] Vec2 CalcEulerAngles(const Vec3& src, const Vec3& dst) noexcept {
    const float dx = dst.x - src.x;
    const float dy = dst.y - src.y;
    const float dz = dst.z - src.z;
    const float distXZ = std::sqrt(dx * dx + dz * dz);
    constexpr float kRadToDeg = 57.2957795f;
    return {-std::atan2(dy, distXZ) * kRadToDeg, std::atan2(dx, dz) * kRadToDeg};
}

[[nodiscard]] float NormalizeAngle(float angle) noexcept {
    while (angle > 180.0f)  angle -= 360.0f;
    while (angle < -180.0f) angle += 360.0f;
    return angle;
}

void* EngineLoop(void*) {
    while (gState.running.load(std::memory_order_relaxed)) {
        if (gState.targetPid <= 0 || kill(gState.targetPid, 0) != 0) {
            gState.targetPid  = FindPidByPackage(gState.pkgName);
            gState.il2cppBase = (gState.targetPid > 0) ? FindLibraryBase(gState.targetPid, "libil2cpp.so") : 0;
            usleep(200000);
            continue;
        }
        if (!gState.il2cppBase) {
            gState.il2cppBase = FindLibraryBase(gState.targetPid, "libil2cpp.so");
            usleep(200000);
            continue;
        }

        const pid_t pid      = gState.targetPid;
        const uintptr_t base = gState.il2cppBase;

        if (gState.aimDrag.load(std::memory_order_relaxed)) {
            const float dragMult = gState.dragMultiplier.load(std::memory_order_relaxed);
            WriteProcMem(pid, base + off::kTouchSensMultiplier, dragMult);
        }

        uintptr_t matchPtr = 0;
        if (!ReadProcMem(pid, base + off::kMatchGame, matchPtr) || !matchPtr) {
            usleep(8000);
            continue;
        }

        uintptr_t localPlayer = 0;
        if (!ReadProcMem(pid, matchPtr + off::kLocalPlayer, localPlayer) || !localPlayer) {
            usleep(8000);
            continue;
        }

        if (gState.controlRecoil.load(std::memory_order_relaxed)) {
            uintptr_t weaponPtr = 0;
            if (ReadProcMem(pid, localPlayer + off::kWeaponDataPtr, weaponPtr) && weaponPtr) {
                const float keep = gState.recoilKeepRatio.load(std::memory_order_relaxed);
                float recoil = 0.0f, scatter = 0.0f;
                if (ReadProcMem(pid, weaponPtr + off::kWeaponRecoilUp, recoil) && recoil > 0.01f) {
                    WriteProcMem(pid, weaponPtr + off::kWeaponRecoilUp, recoil * keep);
                }
                if (ReadProcMem(pid, weaponPtr + off::kWeaponScatter, scatter) && scatter > 0.01f) {
                    WriteProcMem(pid, weaponPtr + off::kWeaponScatter, scatter * keep);
                }
            }
        }

        const bool wantDragHs   = gState.dragHs.load(std::memory_order_relaxed);
        const bool wantDanzWare = gState.danzWareCore.load(std::memory_order_relaxed);
        if (!wantDragHs && !wantDanzWare) {
            usleep(6000);
            continue;
        }

        bool isFiring = false;
        ReadProcMem(pid, localPlayer + off::kIsFiring, isFiring);
        if (!isFiring && !wantDanzWare) {
            usleep(5000);
            continue;
        }

        uint8_t localTeam = 0;
        Vec2 currentAngles{};
        uintptr_t localHeadNode = 0;
        Vec3 localPos{};
        if (!ReadProcMem(pid, localPlayer + off::kTeamId, localTeam) ||
            !ReadProcMem(pid, localPlayer + off::kViewAngles, currentAngles) ||
            !ReadProcMem(pid, localPlayer + off::kHeadTransformNode, localHeadNode) ||
            !ReadTransformPos(pid, localHeadNode, localPos)) {
            usleep(5000);
            continue;
        }

        uintptr_t entityDict = 0;
        if (!ReadProcMem(pid, matchPtr + off::kDictionaryEntities, entityDict) || !entityDict) {
            usleep(5000);
            continue;
        }

        uintptr_t entriesArray = 0;
        int32_t entityCount = 0;
        if (!ReadProcMem(pid, entityDict + off::kDictEntries, entriesArray) ||
            !ReadProcMem(pid, entityDict + off::kDictCount, entityCount) ||
            !entriesArray || entityCount <= 0 || entityCount > 64) {
            usleep(5000);
            continue;
        }

        float bestFov = gState.danzFovDeg.load(std::memory_order_relaxed);
        Vec2 bestTargetAngles{};
        bool foundTarget = false;

        for (int32_t i = 0; i < entityCount; ++i) {
            uintptr_t enemyPtr = 0;
            if (!ReadProcMem(pid, entriesArray + 0x20 + (static_cast<uintptr_t>(i) * 0x18) + 0x10, enemyPtr) ||
                !enemyPtr || enemyPtr == localPlayer) {
                continue;
            }

            bool isDead = true;
            uint8_t teamId = 0;
            if (!ReadProcMem(pid, enemyPtr + off::kIsDead, isDead) ||
                !ReadProcMem(pid, enemyPtr + off::kTeamId, teamId) ||
                isDead || teamId == localTeam) {
                continue;
            }

            uintptr_t headNode = 0, hipNode = 0;
            Vec3 headPos{}, hipPos{};
            if (!ReadProcMem(pid, enemyPtr + off::kHeadTransformNode, headNode) ||
                !ReadProcMem(pid, enemyPtr + off::kHipTransformNode, hipNode) ||
                !ReadTransformPos(pid, headNode, headPos) ||
                !ReadTransformPos(pid, hipNode, hipPos)) {
                continue;
            }

            Vec3 targetPos = headPos;
            if (wantDragHs && !wantDanzWare) {
                targetPos.y = hipPos.y + (headPos.y - hipPos.y) * 0.92f;
            } else if (wantDanzWare) {
                targetPos.y = headPos.y + 0.04f;
            }

            Vec2 desired = CalcEulerAngles(localPos, targetPos);
            float dPitch = NormalizeAngle(desired.x - currentAngles.x);
            float dYaw   = NormalizeAngle(desired.y - currentAngles.y);
            float fovDist = std::sqrt(dPitch * dPitch + dYaw * dYaw);

            if (fovDist < bestFov) {
                bestFov = fovDist;
                bestTargetAngles = {currentAngles.x + dPitch, currentAngles.y + dYaw};
                foundTarget = true;
            }
        }

        if (foundTarget) {
            Vec2 finalAngles = bestTargetAngles;
            if (gState.aimSmooth.load(std::memory_order_relaxed)) {
                const float smooth = std::fmax(1.0f, gState.smoothFactor.load(std::memory_order_relaxed));
                finalAngles.x = currentAngles.x + (bestTargetAngles.x - currentAngles.x) / smooth;
                finalAngles.y = currentAngles.y + (bestTargetAngles.y - currentAngles.y) / smooth;
            }
            if (finalAngles.x > 89.0f)  finalAngles.x = 89.0f;
            if (finalAngles.x < -89.0f) finalAngles.x = -89.0f;
            WriteProcMem(pid, localPlayer + off::kViewAngles, finalAngles);
        }

        usleep(4500);
    }
    return nullptr;
}

extern "C" JNIEXPORT void JNICALL
Java_com_sateam_danzinject_OverlayEngineService_nativeInitEngine(JNIEnv* env, jclass, jstring targetPkg) {
    const char* pkg = env->GetStringUTFChars(targetPkg, nullptr);
    std::strncpy(gState.pkgName, pkg, sizeof(gState.pkgName) - 1);
    gState.targetPid = -1;
    gState.il2cppBase = 0;
    env->ReleaseStringUTFChars(targetPkg, pkg);

    if (!gState.running.exchange(true)) {
        pthread_create(&gWorkerThread, nullptr, EngineLoop, nullptr);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sateam_danzinject_OverlayEngineService_nativeStopEngine(JNIEnv*, jclass) {
    if (gState.running.exchange(false)) {
        pthread_join(gWorkerThread, nullptr);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sateam_danzinject_OverlayEngineService_nativeSetFeature(JNIEnv*, jclass, jint featureId, jboolean enabled) {
    const bool val = (enabled == JNI_TRUE);
    switch (featureId) {
        case 1: gState.dragHs.store(val, std::memory_order_relaxed); break;
        case 2: gState.aimDrag.store(val, std::memory_order_relaxed); break;
        case 3: gState.aimSmooth.store(val, std::memory_order_relaxed); break;
        case 4: gState.controlRecoil.store(val, std::memory_order_relaxed); break;
        case 5: gState.danzWareCore.store(val, std::memory_order_relaxed); break;
        default: break;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_sateam_danzinject_OverlayEngineService_nativeSetParam(JNIEnv*, jclass, jint paramId, jfloat value) {
    switch (paramId) {
        case 10: gState.dragMultiplier.store(std::fmax(1.0f, value / 100.0f), std::memory_order_relaxed); break;
        case 11: gState.smoothFactor.store(std::fmax(1.0f, value / 10.0f), std::memory_order_relaxed); break;
        case 12: gState.recoilKeepRatio.store(std::fmax(0.0f, (100.0f - value) / 100.0f), std::memory_order_relaxed); break;
        case 13: gState.danzFovDeg.store(std::fmax(1.0f, value * 0.1f), std::memory_order_relaxed); break;
        default: break;
    }
}

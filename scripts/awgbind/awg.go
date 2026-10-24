// Package oquqawg — gomobile wrapper exposing the AmneziaWG userspace
// tunnel control surface (the same six functions WireGuard-Android's
// GoBackend declares natively), generated as go.oquqawg.Oquqawg.
//
// Build:  scripts/build_libawg.sh  (requires Go 1.22+, gomobile, Android NDK)
// Consumed by: com.oquq.app.vpn.engine.AwgEngine
package oquqawg

import (
	"sync"

	"github.com/amnezia-vpn/amnezia-wg/conn"
	"github.com/amnezia-vpn/amnezia-wg/device"
	"github.com/amnezia-vpn/amnezia-wg/tun"
	"golang.org/x/sys/unix"
)

var (
	mu      sync.Mutex
	nextTun int = -1
	devices      = map[int]*device.Device{}
)

// WgTurnOn starts a userspace tunnel on the provided TUN fd.
// settings is the userspace IPC format (see AwgConfigBuilder on the Kotlin side):
// private_key/public_key/endpoint/allowed_ip lines plus AmneziaWG Ic/Jc/… extras.
func WgTurnOn(interfaceName string, tunFd int, settings string) int32 {
	mu.Lock()
	defer mu.Unlock()
	nextTun++
	handle := nextTun

	tunDev, err := tun.CreateTUNFromFile(os.NewFile(uintptr(tunFd), interfaceName), 0)
	if err != nil {
		return -1
	}
	dev := device.NewDevice(tunDev, conn.NewDefaultBind(), device.NewLogger(device.LogLevelSilent, "[oQuQ] "))
	if err := dev.IpcSet(settings); err != nil {
		dev.Close()
		return -1
	}
	devices[handle] = dev
	dev.Up()
	return int32(handle)
}

// WgTurnOff stops a tunnel by handle.
func WgTurnOff(handle int32) {
	mu.Lock()
	defer mu.Unlock()
	if dev, ok := devices[int(handle)]; ok {
		dev.Close()
		delete(devices, int(handle))
	}
}

// WgGetSocketV4 returns the raw IPv4 UDP socket fd for VpnService.protect().
func WgGetSocketV4(handle int32) int32 {
	return int32(getSocket(handle, unix.AF_INET))
}

// WgGetSocketV6 returns the raw IPv6 UDP socket fd for VpnService.protect().
func WgGetSocketV6(handle int32) int32 {
	return int32(getSocket(handle, unix.AF_INET6))
}

// WgSetSocketV4 hands the protected socket back after Java-side protect().
func WgSetSocketV4(handle int32, fd int32) int32 {
	return setSocket(handle, unix.AF_INET, int(fd))
}

// WgSetSocketV6 hands the protected socket back after Java-side protect().
func WgSetSocketV6(handle int32, fd int32) int32 {
	return setSocket(handle, unix.AF_INET6, int(fd))
}

// WgVersion reports the AmneziaWG core version.
func WgVersion() string {
	return "amnezia-wg-go (oQuQ build)"
}

func getSocket(handle int32, family int) int {
	mu.Lock()
	defer mu.Unlock()
	dev, ok := devices[int(handle)]
	if !ok {
		return -1
	}
	sock, err := dev.GetSocket(family)
	if err != nil {
		return -1
	}
	return sock
}

func setSocket(handle int32, family int, fd int) int32 {
	mu.Lock()
	defer mu.Unlock()
	dev, ok := devices[int(handle)]
	if !ok {
		return -1
	}
	if err := dev.SetSocket(family, fd); err != nil {
		return -1
	}
	return 0
}

// Package oquqawg — gomobile wrapper exposing the AmneziaWG userspace tunnel
// control surface, modeled 1:1 on the official amneziawg-android glue
// (tunnel/tools/libwg-go/api-android.go) but as a pure-Go gomobile package.
//
// gomobile bind generates class go.oquqawg.Oquqawg consumed by
// com.oquq.app.vpn.engine.AwgEngine.
//
// Socket protection note: VpnService.protect(fd) is sticky on the fd, so the
// Kotlin side polls WgGetSocketV4/V6 and protects; no Set call is needed
// (same as upstream WireGuard/Amnezia GoBackend).
package oquqawg

import (
	"log"
	"math"
	"net"
	"os"
	"runtime/debug"
	"strings"

	"github.com/amnezia-vpn/amneziawg-go/v3/conn"
	"github.com/amnezia-vpn/amneziawg-go/v3/device"
	"github.com/amnezia-vpn/amneziawg-go/v3/ipc"
	"github.com/amnezia-vpn/amneziawg-go/v3/tun"
)

type tunnelHandle struct {
	device *device.Device
	uapi   net.Listener
}

var (
	handles  = map[int32]tunnelHandle{}
	logSink  = log.New(os.Stderr, "[oQuQ/AWG] ", 0)
)

// WgTurnOn starts a userspace tunnel on the provided TUN fd.
// settings follows the wg userspace IPC format (AwgConfigBuilder, Kotlin side):
// private_key/public_key/endpoint/allowed_ip lines plus AmneziaWG Ic/Jc/… extras.
func WgTurnOn(interfaceName string, tunFd int, settings string) int32 {
	logger := &device.Logger{
		Verbosef: func(format string, args ...any) {},
		Errorf:   func(format string, args ...any) { logSink.Printf(format, args...) },
	}

	tunDev, name, err := tun.CreateUnmonitoredTUNFromFD(tunFd)
	if err != nil {
		logger.Errorf("CreateUnmonitoredTUNFromFD: %v", err)
		return -1
	}

	dev := device.NewDevice(tunDev, conn.NewStdNetBind(), logger)
	if err := dev.IpcSet(settings); err != nil {
		logger.Errorf("IpcSet: %v", err)
		dev.Close()
		return -1
	}
	dev.DisableSomeRoamingForBrokenMobileSemantics()

	var uapi net.Listener
	if uapiFile, err := ipc.UAPIOpen(name); err == nil {
		if uapi, err = ipc.UAPIListen(name, uapiFile); err == nil {
			go func() {
				for {
					c, err := uapi.Accept()
					if err != nil {
						return
					}
					go dev.IpcHandle(c)
				}
			}()
		} else {
			uapiFile.Close()
			uapi = nil
		}
	}

	if err := dev.Up(); err != nil {
		logger.Errorf("Up: %v", err)
		if uapi != nil {
			uapi.Close()
		}
		dev.Close()
		return -1
	}

	var i int32
	for i = 0; i < math.MaxInt32; i++ {
		if _, exists := handles[i]; !exists {
			break
		}
	}
	if i == math.MaxInt32 {
		if uapi != nil {
			uapi.Close()
		}
		dev.Close()
		return -1
	}
	handles[i] = tunnelHandle{device: dev, uapi: uapi}
	return i
}

// WgTurnOff stops a tunnel by handle.
func WgTurnOff(handle int32) {
	h, ok := handles[handle]
	if !ok {
		return
	}
	delete(handles, handle)
	if h.uapi != nil {
		h.uapi.Close()
	}
	h.device.Close()
}

// WgGetSocketV4 returns the raw IPv4 UDP socket fd for VpnService.protect().
func WgGetSocketV4(handle int32) int32 {
	h, ok := handles[handle]
	if !ok {
		return -1
	}
	bind, _ := h.device.Bind().(conn.PeekLookAtSocketFd)
	if bind == nil {
		return -1
	}
	fd, err := bind.PeekLookAtSocketFd4()
	if err != nil {
		return -1
	}
	return int32(fd)
}

// WgGetSocketV6 returns the raw IPv6 UDP socket fd for VpnService.protect().
func WgGetSocketV6(handle int32) int32 {
	h, ok := handles[handle]
	if !ok {
		return -1
	}
	bind, _ := h.device.Bind().(conn.PeekLookAtSocketFd)
	if bind == nil {
		return -1
	}
	fd, err := bind.PeekLookAtSocketFd6()
	if err != nil {
		return -1
	}
	return int32(fd)
}

// WgVersion reports the AmneziaWG core version (from build info, like upstream).
func WgVersion() string {
	info, ok := debug.ReadBuildInfo()
	if !ok {
		return "amneziawg-go unknown"
	}
	for _, dep := range info.Deps {
		if dep.Path == "github.com/amnezia-vpn/amneziawg-go/v3" {
			parts := strings.Split(dep.Version, "-")
			if len(parts) == 3 && len(parts[2]) == 12 {
				return "amneziawg-go " + parts[2][:7]
			}
			return "amneziawg-go " + dep.Version
		}
	}
	return "amneziawg-go unknown"
}

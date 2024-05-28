package chipyard.fpga.vc709

import sys.process._

import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.subsystem.{SystemBusKey, PeripheryBusKey, ControlBusKey, ExtMem}
import freechips.rocketchip.devices.debug.{DebugModuleKey, ExportDebug, JTAG}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet}
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import powermonitor.{PeripheryPowerMonitorKey, PowerMonitorParams}

import sifive.fpgashells.shell.{DesignKey}
import sifive.fpgashells.shell.xilinx.{VC7094GDDRSize}

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem, ExtTLMem}
import chipyard.harness._

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripheryPowerMonitorKey => 
    List(PowerMonitorParams(address = BigInt(0x64001000L), clockFreqMHz = 50, maxSamples = 0x8000))
})

class WithSystemModifications extends Config((site, here, up) => {
  case DTSTimebase => BigInt{(1e6).toLong}
  case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VC7094GDDRSize)))) // set extmem to DDR size (note the size)
  case SerialTLKey => None // remove serialized tl port
})

class WithVC709Tweaks extends Config (
  // clocking
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new chipyard.clocking.WithPassthroughClockGenerator ++
  new chipyard.config.WithMemoryBusFrequency(50.0) ++
  new chipyard.config.WithSystemBusFrequency(50.0) ++
  new chipyard.config.WithPeripheryBusFrequency(50.0) ++
  new chipyard.harness.WithHarnessBinderClockFreqMHz(50) ++
  // harness binders
  new chipyard.harness.WithAllClocksFromHarnessClockInstantiator ++
  new WithVC709UARTHarnessBinder ++
  new WithVC709PMBusHarnessBinder ++
  new WithJTAGDebugBScan ++
  new WithVC709DDRMemHarnessBinder ++
  // io binders
  new WithUARTIOPassthrough ++
  new WithPMBusIOPassthrough ++
  new WithTLIOPassthrough ++
  // other configuration
  new WithDefaultPeripherals ++
  new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use sdboot bootrom, setup ext. mem. size
  // new chipyard.config.WithNoDebug ++ // remove debug module
  new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  new freechips.rocketchip.subsystem.WithNMemoryChannels(1)
)

class TinyRocketVC709Config extends Config(
  new WithVC709Tweaks ++
  new freechips.rocketchip.subsystem.WithNBreakpoints(2) ++
  new freechips.rocketchip.subsystem.WithL1DCacheSets(1024) ++ // increase L1D$ size to 64KiB
  new chipyard.TinyRocketConfig
)

class RocketVC709Config extends Config (
  new WithVC709Tweaks ++
  new chipyard.RocketConfig
)

class HBICAPRocketVC709Config extends Config (
  new WithVC709Tweaks ++
  new xilinxips.hbicap.WithHBICAP(BigInt(0x64002000L), BigInt(0x7AA00000L)) ++
  new chipyard.RocketConfig
)

class Rocket128VC709Config extends Config (
  new WithVC709Tweaks ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new chipyard.RocketConfig
)

class CGRARocket64VC709Config extends Config (
  new WithVC709Tweaks ++
  new chipyard.config.WithSystemBusWidth(64) ++
  new overlay.DefaultOverlayConfig ++
  new overlay.WithOverlayRocc ++
  new chipyard.RocketConfig
)

class CGRARocket128VC709Config extends Config (
  new WithVC709Tweaks ++
  new chipyard.config.WithSystemBusWidth(128) ++
  new overlay.DefaultOverlayConfig ++
  new overlay.WithOverlayRocc ++
  new chipyard.RocketConfig
)

class CGRARocket256VC709Config extends Config (
  new WithVC709Tweaks ++
  new chipyard.config.WithSystemBusWidth(256) ++
  new overlay.DefaultOverlayConfig ++
  new overlay.WithOverlayRocc ++
  new chipyard.RocketConfig
)

class HwachaRocketVC709Config extends Config(
  new WithVC709Tweaks ++
  new chipyard.HwachaRocketConfig
)

class BoomVC709Config extends Config (
  new WithFPGAFrequency(50) ++
  new WithVC709Tweaks ++
  new chipyard.MegaBoomConfig
)

class WithFPGAFrequency(fMHz: Double) extends Config (
  new chipyard.config.WithPeripheryBusFrequency(fMHz) ++ // assumes using PBUS as default freq.
  new chipyard.config.WithMemoryBusFrequency(fMHz)
)

class WithFPGAFreq25MHz extends WithFPGAFrequency(25)
class WithFPGAFreq50MHz extends WithFPGAFrequency(50)
class WithFPGAFreq75MHz extends WithFPGAFrequency(75)
class WithFPGAFreq100MHz extends WithFPGAFrequency(100)

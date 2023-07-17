package chipyard.fpga.vc709

import sys.process._

import org.chipsalliance.cde.config.{Config, Parameters}
import freechips.rocketchip.subsystem.{SystemBusKey, PeripheryBusKey, ControlBusKey, ExtMem}
import freechips.rocketchip.devices.debug.{DebugModuleKey, ExportDebug, JTAG}
// import freechips.rocketchip.devices.tilelink.{DevNullParams, BootROMLocated}
import freechips.rocketchip.diplomacy.{DTSModel, DTSTimebase, RegionType, AddressSet}
import freechips.rocketchip.tile.{XLen}

import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTParams}
import sifive.blocks.devices.i2c.{PeripheryI2CKey, I2CParams}

import sifive.fpgashells.shell.{DesignKey}
// import sifive.fpgashells.shell.xilinx.{VC7094GDDRSize}

import testchipip.{SerialTLKey}

import chipyard.{BuildSystem, ExtTLMem, DefaultClockFrequencyKey}

class WithDefaultPeripherals extends Config((site, here, up) => {
  case PeripheryUARTKey => List(UARTParams(address = BigInt(0x64000000L)))
  case PeripheryI2CKey => List(I2CParams(address = BigInt(0x64001000L)))
})

class WithSystemModifications extends Config((site, here, up) => {
  case DTSTimebase => BigInt{(1e6).toLong}
  // case ExtMem => up(ExtMem, site).map(x => x.copy(master = x.master.copy(size = site(VC7094GDDRSize)))) // set extmem to DDR size (note the size)
  case SerialTLKey => None // remove serialized tl port
})

class WithVC709Tweaks extends Config (
  // harness binders
  new WithVC709UARTHarnessBinder ++
  new WithVC709PMBusHarnessBinder ++
  new WithJTAGDebugBScan ++
  // new WithVC709DDRMemHarnessBinder ++
  // io binders
  new WithUARTIOPassthrough ++
  new WithPMBusIOPassthrough ++
  // new WithTLIOPassthrough ++
  // other configuration
  new WithDefaultPeripherals ++
  // new chipyard.config.WithTLBackingMemory ++ // use TL backing memory
  new WithSystemModifications ++ // setup busses, use sdboot bootrom, setup ext. mem. size
  // new chipyard.config.WithNoDebug ++ // remove debug module
  // new freechips.rocketchip.subsystem.WithoutTLMonitors ++
  // new freechips.rocketchip.subsystem.WithNMemoryChannels(1) ++
  new WithFPGAFrequency(50) // default 50MHz freq
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

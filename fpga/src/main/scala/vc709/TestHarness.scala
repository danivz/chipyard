package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp, BundleBridgeSource}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.tilelink.{TLClientNode}

import sifive.fpgashells.shell.xilinx.{VC709Shell, UARTVC709ShellPlacer}
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell.{ClockInputOverlayKey, ClockInputDesignInput, UARTOverlayKey, UARTDesignInput, UARTShellInput, LEDOverlayKey, LEDDesignInput, SwitchOverlayKey, SwitchDesignInput, ButtonOverlayKey, ButtonDesignInput, PCIeOverlayKey, PCIeDesignInput, PCIeShellInput, DDROverlayKey, DDRDesignInput, I2COverlayKey, I2CDesignInput, JTAGDebugBScanOverlayKey, JTAGDebugBScanDesignInput}
import sifive.fpgashells.clocks.{ClockGroup, ClockSinkNode, PLLFactoryKey, ResetWrangler}

import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTPortIO}
import sifive.blocks.devices.i2c.{PeripheryI2CKey, I2CPort}

import chipyard.{HasHarnessSignalReferences, BuildTop, ChipTop, ExtTLMem, CanHaveMasterTLMemPort, DefaultClockFrequencyKey}
import chipyard.iobinders.{HasIOBinders}
import chipyard.harness.{ApplyHarnessBinders}

class VC709FPGATestHarness(override implicit val p: Parameters) extends VC709Shell { outer =>

  def dp = designParameters

  // Order matters; ddr depends on sys_clock
  val uart = Overlay(UARTOverlayKey, new UARTVC709ShellPlacer(this, UARTShellInput()))

  val topDesign = LazyModule(p(BuildTop)(dp)).suggestName("chiptop")

  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey).head.place(ClockInputDesignInput()).overlayOutput.node

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  println(s"VC709 FPGA Base Clock Freq: ${dp(DefaultClockFrequencyKey)} MHz")
  val dutClock = ClockSinkNode(freqMHz = dp(DefaultClockFrequencyKey))
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL

  // /*** LED ***/
  // val ledModule = dp(LEDOverlayKey).map(_.place(LEDDesignInput()).overlayOutput.led)

  // /*** Switch ***/
  // val switchModule = dp(SwitchOverlayKey).map(_.place(SwitchDesignInput()).overlayOutput.sw)

  // /*** Button ***/
  // val buttonModule = dp(ButtonOverlayKey).map(_.place(ButtonDesignInput()).overlayOutput.but)

  /*** PMBus ***/
  val io_pmbus_bb = BundleBridgeSource(() => (new I2CPort))
  dp(I2COverlayKey).head.place(I2CDesignInput(io_pmbus_bb))

  /*** JTAG ***/
  val jtagModule = dp(JTAGDebugBScanOverlayKey).head.place(JTAGDebugBScanDesignInput()).overlayOutput.jtag

  /*** UART ***/

  // 1st UART goes to the VC709 dedicated UART

  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  // /*** DDR ***/

  // // Modify the last field of `DDRDesignInput` for 1GB RAM size
  // val ddrNode = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL, true)).overlayOutput.ddr

  // // connect 1 mem. channel to the FPGA DDR
  // val inParams = topDesign match { case td: ChipTop =>
  //   td.lazySystem match { case lsys: CanHaveMasterTLMemPort =>
  //     lsys.memTLNode.edges.in(0)
  //   }
  // }
  // val ddrClient = TLClientNode(Seq(inParams.master))
  // ddrNode := ddrClient

  // module implementation
  override lazy val module = new VC709FPGATestHarnessImp(this)
}

class VC709FPGATestHarnessImp(_outer: VC709FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessSignalReferences {

  val vc709Outer = _outer

  val reset = IO(Input(Bool()))
  _outer.xdc.addBoardPin(reset, "reset")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  // val ereset: Bool = _outer.chiplink.get() match {
  //   case Some(x: ChipLinkVC709PlacedOverlay) => !x.ereset_n
  //   case _ => false.B
  // }

  _outer.pllReset := (resetIBUF.io.O || powerOnReset /*|| ereset*/)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  val buildtopClock = _outer.dutClock.in.head._1.clock
  val buildtopReset = WireInit(hReset)
  val dutReset = hReset.asAsyncReset
  val success = false.B

  childClock := buildtopClock
  childReset := buildtopReset

  // harness binders are non-lazy
  _outer.topDesign match { case d: HasIOBinders =>
    ApplyHarnessBinders(this, d.lazySystem, d.portMap)
  }

  // check the top-level reference clock is equal to the default
  // non-exhaustive since you need all ChipTop clocks to equal the default
  require(getRefClockFreq == p(DefaultClockFrequencyKey))
}

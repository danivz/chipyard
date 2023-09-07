package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{IO}

import freechips.rocketchip.diplomacy.{LazyModule, LazyRawModuleImp, BundleBridgeSource}
import org.chipsalliance.cde.config.{Parameters}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.subsystem.{SystemBusKey}
import freechips.rocketchip.diplomacy.{IdRange, TransferSizes}

import sifive.fpgashells.shell.xilinx.{VC709Shell, UARTVC709ShellPlacer}
import sifive.fpgashells.ip.xilinx.{IBUF, PowerOnResetFPGAOnly}
import sifive.fpgashells.shell._
import sifive.fpgashells.clocks.{ClockGroup, ClockSinkNode, PLLFactoryKey, ResetWrangler}

import sifive.blocks.devices.uart.{PeripheryUARTKey, UARTPortIO}
import powermonitor.{PMBusPort}

import chipyard._
import chipyard.iobinders.{HasIOBinders}
import chipyard.harness._

class VC709FPGATestHarness(override implicit val p: Parameters) extends VC709Shell { outer =>

  def dp = designParameters

  // Order matters; ddr depends on sys_clock
  val uart = Overlay(UARTOverlayKey, new UARTVC709ShellPlacer(this, UARTShellInput()))

  // place all clocks in the shell
  require(dp(ClockInputOverlayKey).size >= 1)
  val sysClkNode = dp(ClockInputOverlayKey).head.place(ClockInputDesignInput()).overlayOutput.node

  /*** Connect/Generate clocks ***/

  // connect to the PLL that will generate multiple clocks
  val harnessSysPLL = dp(PLLFactoryKey)()
  harnessSysPLL := sysClkNode

  // create and connect to the dutClock
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"VC709 FPGA Base Clock Freq: ${dutFreqMHz} MHz")
  val dutWrangler = LazyModule(new ResetWrangler)
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLL

  /*** LED ***/
  val ledModule = dp(LEDOverlayKey).map(_.place(LEDDesignInput()).overlayOutput.led)

  /*** Switch ***/
  val switchModule = dp(SwitchOverlayKey).map(_.place(SwitchDesignInput()).overlayOutput.sw)

  /*** Button ***/
  val buttonModule = dp(ButtonOverlayKey).map(_.place(ButtonDesignInput()).overlayOutput.but)

  /*** PMBus ***/
  val io_pmbus_bb = BundleBridgeSource(() => (new PMBusPort))
  dp(PMBusOverlayKey).head.place(PMBusDesignInput(io_pmbus_bb))

  /*** JTAG ***/
  val jtagModule = dp(JTAGDebugBScanOverlayKey).head.place(JTAGDebugBScanDesignInput()).overlayOutput.jtag

  /*** UART ***/

  // 1st UART goes to the VC709 dedicated UART

  val io_uart_bb = BundleBridgeSource(() => (new UARTPortIO(dp(PeripheryUARTKey).head)))
  dp(UARTOverlayKey).head.place(UARTDesignInput(io_uart_bb))

  /*** DDR ***/

  // Modify the last field of `DDRDesignInput` for 1GB RAM size
  val ddrNode = dp(DDROverlayKey).head.place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLL, true)).overlayOutput.ddr
  val ddrClient = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name = "chip_ddr",
    sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
  )))))

  ddrNode := ddrClient

  // module implementation
  override lazy val module = new VC709FPGATestHarnessImp(this)
}

class VC709FPGATestHarnessImp(_outer: VC709FPGATestHarness) extends LazyRawModuleImp(_outer) with HasHarnessInstantiators {
  val vc709Outer = _outer

  val reset = IO(Input(Bool()))
  _outer.xdc.addBoardPin(reset, "reset")

  val resetIBUF = Module(new IBUF)
  resetIBUF.io.I := reset

  val sysclk: Clock = _outer.sysClkNode.out.head._1.clock

  val powerOnReset: Bool = PowerOnResetFPGAOnly(sysclk)
  _outer.sdc.addAsyncPath(Seq(powerOnReset))

  _outer.pllReset := (resetIBUF.io.O || powerOnReset)

  // reset setup
  val hReset = Wire(Reset())
  hReset := _outer.dutClock.in.head._1.reset

  def referenceClockFreqMHz = _outer.dutFreqMHz
  def referenceClock = _outer.dutClock.in.head._1.clock
  def referenceReset = hReset
  def success = { require(false, "Unused"); false.B }

  childClock := referenceClock
  childReset := referenceReset

  instantiateChipTops()
}

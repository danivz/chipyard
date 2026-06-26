package chipyard.fpga.vc709

import chisel3._

import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{UARTPortIO}

import chipyard._
import chipyard.harness._
import chipyard.iobinders._

/*** UART ***/
class WithVC709UARTHarnessBinder extends HarnessBinder({
  case (th: VC709FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.vc709Outer.io_uart_bb.bundle <> port.io
  }
})

/*** Experimental DDR ***/
class WithVC709DDRMemHarnessBinder extends HarnessBinder({
  case (th: VC709FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    val bundles = th.vc709Outer.ddrClient.out.map(_._1)
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io
  }
})

/*** JTAG BScan ***/
class WithVC709JTAGHarnessBinder extends HarnessBinder({
  case (th: VC709FPGATestHarnessImp, port: JTAGPort, chipId: Int) => {
    val jtag_io = th.vc709Outer.jtagModule.getWrappedValue
    port.io.TCK := jtag_io.TCK
    port.io.TMS := jtag_io.TMS
    port.io.TDI := jtag_io.TDI
    port.io.reset.foreach(_ := th.referenceReset)
    jtag_io.TDO.data := port.io.TDO
    jtag_io.TDO.driven := true.B
    // ignore srst_n
    jtag_io.srst_n := DontCare
  }
})

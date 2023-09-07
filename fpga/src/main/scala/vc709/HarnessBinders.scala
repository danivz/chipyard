package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{BaseModule}

import freechips.rocketchip.devices.debug.{HasPeripheryDebug}
import freechips.rocketchip.jtag.{JTAGIO}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import powermonitor.{HasPeripheryPowerMonitorModuleImp, PMBusPort}

import chipyard.{CanHaveMasterTLMemPort}
import chipyard.harness.{OverrideHarnessBinder}
import chipyard.iobinders.JTAGChipIO

/*** UART ***/
class WithVC709UARTHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule, ports: Seq[UARTPortIO]) => {
    th match { case vc709th: VC709FPGATestHarnessImp => {
      vc709th.vc709Outer.io_uart_bb.bundle <> ports.head
    }}
  }
})

/*** PMBus ***/
class WithVC709PMBusHarnessBinder extends OverrideHarnessBinder({
  (system: HasPeripheryPowerMonitorModuleImp, th: BaseModule, ports: Seq[PMBusPort]) => {
    th match { case vc709th: VC709FPGATestHarnessImp => {
      vc709th.vc709Outer.io_pmbus_bb.bundle <> ports.head
    }}
  }
})

/*** Experimental DDR ***/
class WithVC709DDRMemHarnessBinder extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: BaseModule, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    th match { case vc709th: VC709FPGATestHarnessImp => {
      require(ports.size == 1)

      val bundles = vc709th.vc709Outer.ddrClient.out.map(_._1)
      val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
      bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
      ddrClientBundle <> ports.head
    }}
  }
})

/*** JTAG BScan ***/
class WithJTAGDebugBScan extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: BaseModule, ports: Seq[Data]) => {
    th match { case vc709th: VC709FPGATestHarnessImp => {
      ports.map {
        case j: JTAGChipIO => {
          val jtag_tmp = vc709th.vc709Outer.jtagModule
          jtag_tmp.TDO.data := j.TDO
          jtag_tmp.TDO.driven := true.B
          j.TCK := jtag_tmp.TCK
          j.TMS := jtag_tmp.TMS
          j.TDI := jtag_tmp.TDI
      } }
    } }
  }
})

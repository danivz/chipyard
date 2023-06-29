package chipyard.fpga.vcu118

import chisel3._
import chisel3.experimental.{BaseModule}

import freechips.rocketchip.devices.debug.{HasPeripheryDebug}
import freechips.rocketchip.jtag.{JTAGIO}

import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp, UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}

import chipyard.{HasHarnessSignalReferences, CanHaveMasterTLMemPort}
import chipyard.harness.{OverrideHarnessBinder}
import chipyard.iobinders.JTAGChipIO

/*** UART ***/
class WithUART extends OverrideHarnessBinder({
  (system: HasPeripheryUARTModuleImp, th: BaseModule with HasHarnessSignalReferences, ports: Seq[UARTPortIO]) => {
    th match { case vcu118th: VCU118FPGATestHarnessImp => {
      vcu118th.vcu118Outer.io_uart_bb.bundle <> ports.head
    } }
  }
})

/*** SPI ***/
class WithSPISDCard extends OverrideHarnessBinder({
  (system: HasPeripherySPI, th: BaseModule with HasHarnessSignalReferences, ports: Seq[SPIPortIO]) => {
    th match { case vcu118th: VCU118FPGATestHarnessImp => {
      vcu118th.vcu118Outer.io_spi_bb.bundle <> ports.head
    } }
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends OverrideHarnessBinder({
  (system: CanHaveMasterTLMemPort, th: BaseModule with HasHarnessSignalReferences, ports: Seq[HeterogeneousBag[TLBundle]]) => {
    th match { case vcu118th: VCU118FPGATestHarnessImp => {
      require(ports.size == 1)

      val bundles = vcu118th.vcu118Outer.ddrClient.out.map(_._1)
      val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
      bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
      ddrClientBundle <> ports.head
    } }
  }
})

/*** JTAG BScan ***/
class WithJTAGDebugBScan extends OverrideHarnessBinder({
  (system: HasPeripheryDebug, th: BaseModule with HasHarnessSignalReferences, ports: Seq[Data]) => {
    th match { case vcu118th: VCU118FPGATestHarnessImp => {
      ports.map {
        case j: JTAGChipIO => withClockAndReset(th.buildtopClock, th.buildtopReset) {
          val jtag_tmp = vcu118th.vcu118Outer.jtagNode
          jtag_tmp.TDO.data := j.TDO
          jtag_tmp.TDO.driven := true.B
          j.TCK := jtag_tmp.TCK
          j.TMS := jtag_tmp.TMS
          j.TDI := jtag_tmp.TDI
      } }
    } }
  }
})

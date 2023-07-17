package chipyard.fpga.vc709

import chisel3._
import chisel3.experimental.{IO, DataMirror}

import freechips.rocketchip.diplomacy.{ResourceBinding, Resource, ResourceAddress, InModuleBody}
import freechips.rocketchip.subsystem.{BaseSubsystem}
import freechips.rocketchip.util.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{HasPeripheryUARTModuleImp}
import sifive.blocks.devices.i2c.{HasPeripheryI2CModuleImp}

import chipyard.{CanHaveMasterTLMemPort}
import chipyard.iobinders.{OverrideIOBinder, OverrideLazyIOBinder}

class WithUARTIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryUARTModuleImp) => {
    val io_uart_pins_temp = system.uart.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"uart_$i") }
    (io_uart_pins_temp zip system.uart).map { case (io, sysio) =>
      io <> sysio
    }
    (io_uart_pins_temp, Nil)
  }
})

class WithPMBusIOPassthrough extends OverrideIOBinder({
  (system: HasPeripheryI2CModuleImp) => {
    val io_i2c_pins_temp = system.i2c.zipWithIndex.map { case (dio, i) => IO(dio.cloneType).suggestName(s"pmbus_$i") }
    (io_i2c_pins_temp zip system.i2c).map { case (io, sysio) =>
      io <> sysio
    }
    (io_i2c_pins_temp, Nil)
  }
})

class WithTLIOPassthrough extends OverrideIOBinder({
  (system: CanHaveMasterTLMemPort) => {
    val io_tl_mem_pins_temp = IO(DataMirror.internal.chiselTypeClone[HeterogeneousBag[TLBundle]](system.mem_tl)).suggestName("tl_slave")
    io_tl_mem_pins_temp <> system.mem_tl
    (Seq(io_tl_mem_pins_temp), Nil)
  }
})

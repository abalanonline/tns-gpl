/*
 * Copyright (C) 2025 Aleksei Balan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ab.tns;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Use virtual midi ports if available.
 * Linux:
 * sudo modprobe snd_virmidi midi_devs=3
 * connect alsa midi ports with aconnect
 * Windows:
 * loopMIDI, connect with MIDI-OX
 */
public class VirMidi implements AutoCloseable {

  private static final Logger log = Logger.getLogger(VirMidi.class.getName());

  private MidiDevice transmitterDevice;
  private List<MidiDevice> receiverDevices = new ArrayList<>();
  private List<Consumer<ShortMessage>> receivers = new ArrayList<>();
  private Consumer<ShortMessage> consumer;
  private final VirMidiReceiver virMidiReceiver = new VirMidiReceiver();

  // midi devices java:
  // Gervill / OpenJDK / 1.0 / Software MIDI Synthesizer
  // Real Time Sequencer / Oracle Corporation / Version 1.0 / Software sequencer
  // windows:
  // Microsoft MIDI Mapper / Unknown vendor / 5.0 / Windows MIDI_MAPPER
  // Microsoft GS Wavetable Synth / Unknown vendor / 1.0 / Internal software synthesizer

  // linux vendor: ALSA (http://www.alsa-project.org)
  // linux version: uname -r
  // windows vendor: Unknown vendor
  // windows description receiver: External MIDI Port
  // windows description transmitter: No details available

  public VirMidi open() {
    // list of devices except preset
    List<MidiDevice> midiDevices = Arrays.stream(MidiSystem.getMidiDeviceInfo()).filter(d -> {
      final String vendor = d.getVendor();
      final String name = d.getName();
      return (!(vendor.equals("OpenJDK") || vendor.startsWith("Oracle ") || name.startsWith("Microsoft ")
          || (name.startsWith("VirMIDI ") && !name.endsWith(",0]"))));
    }).map(info -> {
      try {
        return MidiSystem.getMidiDevice(info);
      } catch (MidiUnavailableException e) {
        return null;
      }
    }).filter(Objects::nonNull).collect(Collectors.toList());

    // log device info
    if (log.isLoggable(Level.CONFIG)) midiDevices.forEach(d -> {
      log.config(d.getDeviceInfo().getName()
          + (d.getMaxTransmitters() == 0 ? " ---" : String.format(" i%02d", d.getMaxTransmitters()))
          + (d.getMaxReceivers() == 0 ? " ---" : String.format(" o%02d", d.getMaxReceivers()))
          + " " + d.getClass().getSimpleName());
    });

    // list of VirMIDI devices
    List<MidiDevice> virMidiDevices = midiDevices.stream().filter(d -> {
      String name = d.getDeviceInfo().getName();
      return (name.startsWith("VirMIDI ") && name.endsWith(",0]") || name.startsWith("loopMIDI Port"));
    }).collect(Collectors.toList());
    if (virMidiDevices.isEmpty()) virMidiDevices = midiDevices; // just connect to any hardware midi

    for (MidiDevice device : virMidiDevices) {
      if (transmitterDevice == null && device.getMaxTransmitters() != 0) {
        try {
          device.open();
          transmitterDevice = device;
          final Transmitter transmitter = device.getTransmitter();
          transmitter.setReceiver(virMidiReceiver);
        } catch (MidiUnavailableException e) {
          continue; // expected if device is busy
        }
      }
      if (device.getMaxReceivers() != 0) {
        try {
          device.open();
          receiverDevices.add(device);
          final Receiver receiver = device.getReceiver();
          receivers.add(m -> receiver.send(m, -1));
        } catch (MidiUnavailableException e) {
          continue; // expected if device is busy
        }
      }
    }

    // log device info
    if (log.isLoggable(Level.CONFIG)) {
      if (transmitterDevice != null) {
        final MidiDevice.Info deviceInfo = transmitterDevice.getDeviceInfo();
        log.config("<- " + deviceInfo.getName() + "\t" + deviceInfo.getVersion());
      }
      for (MidiDevice receiverDevice : receiverDevices) {
        final MidiDevice.Info deviceInfo = receiverDevice.getDeviceInfo();
        log.config("-> " + deviceInfo.getName() + "\t" + deviceInfo.getVersion());
      }
    }

    return this;
  }

  private class VirMidiReceiver implements Receiver {
    @Override
    public void send(MidiMessage message, long timeStamp) {
      final Consumer<ShortMessage> consumer = VirMidi.this.consumer;
      if (consumer == null) return;
      if (!(message instanceof ShortMessage)) return; // discard sysex messages
      ShortMessage shortMessage = (ShortMessage) message;
      consumer.accept(shortMessage);
    }

    @Override
    public void close() {}
  }

  @Override
  public void close() {
    if (transmitterDevice != null) transmitterDevice.close();
    transmitterDevice = null;
    receiverDevices.forEach(MidiDevice::close);
    receiverDevices.clear();
    receivers.clear();
  }

  /**
   * Named after {@link javax.sound.midi.Transmitter#setReceiver}
   * @param consumer the function that will consume midi messages from device
   */
  public void setReceiver(Consumer<ShortMessage> consumer) {
    this.consumer = consumer;
  }

  /**
   * Named after {@link javax.sound.midi.MidiDevice#getReceivers}
   * @return a list of consumers of midi messages to be sent to device(s)
   */
  public List<Consumer<ShortMessage>> getReceivers() {
    return receivers;
  }

  public static void main(String[] args) {
    try (Synthesizer synthesizer = MidiSystem.getSynthesizer(); VirMidi virMidi = new VirMidi().open()) {
      synthesizer.open();
      final Receiver receiver = synthesizer.getReceiver();
      virMidi.setReceiver(shortMessage -> receiver.send(shortMessage, -1));
      System.in.read();
      virMidi.setReceiver(null);
    } catch (MidiUnavailableException | IOException e) {
      throw new RuntimeException(e);
    }
  }

}

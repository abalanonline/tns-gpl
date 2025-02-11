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
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * API adapter to use VirMidi as a MidiDevice.
 */
public class VirMidiDevice implements MidiDevice {

  private static final Info DEVICE_INFO = new Info("VirMidiDevice", "TNS", "VirMidi", "1.0") {};
  private VirMidi virMidi;
  private final Transmitter transmitter = new DeviceTransmitter();
  private Receiver receiver;

  @Override
  public Info getDeviceInfo() {
    return DEVICE_INFO;
  }

  @Override
  public void open() {
    virMidi = new VirMidi();
    virMidi.open();
    virMidi.setReceiver(this::send);
  }

  @Override
  public void close() {
    virMidi.setReceiver(null);
    virMidi.close();
    virMidi = null;
  }

  @Override
  public boolean isOpen() {
    return virMidi != null;
  }

  @Override
  public long getMicrosecondPosition() {
    return 0;
  }

  @Override
  public int getMaxReceivers() {
    final VirMidi virMidi = this.virMidi;
    return virMidi == null ? 0 : virMidi.getReceivers().size();
  }

  @Override
  public int getMaxTransmitters() {
    return 1;
  }

  private void send(ShortMessage message) {
    final Receiver receiver = this.receiver;
    if (receiver != null) receiver.send(message, -1);
  }

  private class DeviceTransmitter implements Transmitter {
    @Override
    public void setReceiver(Receiver receiver) {
      VirMidiDevice.this.receiver = receiver;
    }
    @Override
    public Receiver getReceiver() {
      return VirMidiDevice.this.receiver;
    }
    @Override
    public void close() {}
  }

  private static class DeviceReceiver implements Receiver {
    private final Consumer<ShortMessage> consumer;
    public DeviceReceiver(Consumer<ShortMessage> consumer) {
      this.consumer = consumer;
    }
    @Override
    public void send(MidiMessage message, long timeStamp) {
      if (!(message instanceof ShortMessage)) return; // discard sysex messages
      ShortMessage shortMessage = (ShortMessage) message;
      consumer.accept(shortMessage);
    }
    @Override
    public void close() {}
  }

  @Override
  public Receiver getReceiver() {
    final VirMidi virMidi = this.virMidi;
    if (virMidi == null) return null;
    final List<Consumer<ShortMessage>> receivers = virMidi.getReceivers();
    if (receivers.isEmpty()) return null;
    return new DeviceReceiver(receivers.get(0));
  }

  @Override
  public List<Receiver> getReceivers() {
    final VirMidi virMidi = this.virMidi;
    if (virMidi == null) return Collections.emptyList();
    final List<Consumer<ShortMessage>> receivers = virMidi.getReceivers();
    return receivers.stream().map(DeviceReceiver::new).collect(Collectors.toList());
  }

  @Override
  public Transmitter getTransmitter() {
    return transmitter;
  }

  @Override
  public List<Transmitter> getTransmitters() {
    return Collections.singletonList(transmitter);
  }

  public static void main(String[] args) {
    try (Synthesizer synthesizer = MidiSystem.getSynthesizer(); VirMidiDevice virMidiDevice = new VirMidiDevice()) {
      synthesizer.open();
      virMidiDevice.open();
      final Transmitter transmitter = virMidiDevice.getTransmitter();
      transmitter.setReceiver(synthesizer.getReceiver());
      System.in.read();
      transmitter.setReceiver(null);
    } catch (MidiUnavailableException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}

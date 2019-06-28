/*
 * Copyright (c) 2011, Swedish Institute of Computer Science.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the Institute nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE INSTITUTE AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE INSTITUTE OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 *
 */

package org.contikios.cooja.plugins.vanet.config;

import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Random;
import java.util.Vector;

import javax.swing.tree.DefaultMutableTreeNode;

import org.apache.log4j.Logger;
import org.jdom.Element;

import org.contikios.cooja.Simulation;
import org.contikios.cooja.interfaces.DirectionalAntennaRadio;
import org.contikios.cooja.interfaces.Radio;
import org.contikios.cooja.radiomediums.AbstractRadioMedium;
import org.contikios.cooja.util.ScnObservable;

/**
 * TODO
 * @author Fredrik Osterlind
 * @author Patrick Rathje
 */
public class VanetConfig {
  private static Logger logger = Logger.getLogger(VanetConfig.class);

  private Hashtable<Parameter,Object> parametersDefaults = new Hashtable<Parameter,Object>();
  private Hashtable<Parameter,Object> parameters = new Hashtable<Parameter,Object>();

  /**
   * Notifies observers when this channel model has changed settings.
   */
  private ScnObservable settingsObservable = new ScnObservable();
  public enum Parameter {
    log_dir,
    screen_export_dir,
    vehicles_per_hour;

    public static Object getDefaultValue(Parameter p) {
      switch (p) {
      case log_dir:
        return new String("vanet");
      case vehicles_per_hour:
        return new Double(200.0);
          case screen_export_dir:
          return new String(); // empty
      }
      throw new RuntimeException("Unknown default value: " + p);
    }
  }
  
  public VanetConfig() {

    /* Default values */
    for (Parameter p: Parameter.values()) {
      parameters.put(p, Parameter.getDefaultValue(p));
    }
    parametersDefaults = (Hashtable<Parameter,Object>) parameters.clone();

  }

  /**
   * Adds a settings observer to this channel model.
   * Every time the settings are changed all observers
   * will be notified.
   *
   * @param obs New observer
   */
  public void addSettingsObserver(Observer obs) {
    settingsObservable.addObserver(obs);
  }

  /**
   * Deletes an earlier registered setting observer.
   *
   * @param osb
   *          Earlier registered observer
   */
  public void deleteSettingsObserver(Observer obs) {
    settingsObservable.deleteObserver(obs);
  }

  /**
   * Returns a parameter value
   *
   * @param id Parameter identifier
   * @return Current parameter value
   */
  public Object getParameterValue(Parameter id) {
    Object value = parameters.get(id);
    if (value == null) {
      logger.fatal("No parameter with id:" + id + ", aborting");
      return null;
    }
    return value;
  }

  /**
   * Returns a double parameter value
   *
   * @param identifier Parameter identifier
   * @return Current parameter value
   */
  public double getParameterDoubleValue(Parameter id) {
    return (Double) getParameterValue(id);
  }

  /**
   * Returns an integer parameter value
   *
   * @param identifier Parameter identifier
   * @return Current parameter value
   */
  public int getParameterIntegerValue(Parameter id) {
    return (Integer) getParameterValue(id);
  }

  /**
   * Returns a boolean parameter value
   *
   * @param identifier Parameter identifier
   * @return Current parameter value
   */
  public boolean getParameterBooleanValue(Parameter id) {
    return (Boolean) getParameterValue(id);
  }

  /**
   * Saves a new parameter value
   *
   * @param id Parameter identifier
   * @param newValue New parameter value
   */
  public void setParameterValue(Parameter id, Object newValue) {
    if (!parameters.containsKey(id)) {
      logger.fatal("No parameter with id:" + id + ", aborting");
      return;
    }
    parameters.put(id, newValue);
    notifySettingsChanged();
  }

  /**
   * When this method is called all settings observers
   * will be notified.
   */
  public void notifySettingsChanged() {
    settingsObservable.setChangedAndNotify();
  }

  /**
   * Returns XML elements representing the current configuration.
   *
   * @see #setConfigXML(Collection)
   * @return XML element collection
   */
  public Collection<Element> getConfigXML() {
    ArrayList<Element> config = new ArrayList<Element>();
    Element element;

    Enumeration<Parameter> paramEnum = parameters.keys();
    while (paramEnum.hasMoreElements()) {
      Parameter p = (Parameter) paramEnum.nextElement();
      element = new Element(p.toString());
      if (parametersDefaults.get(p).equals(parameters.get(p))) {
        /* Default value */
        continue;
      }
      element.setAttribute("value", parameters.get(p).toString());
      config.add(element);
    }
    return config;
  }

  /**
   * Sets the configuration depending on the given XML elements.
   *
   * @see #getConfigXML()
   * @param configXML
   *          Config XML elements
   * @return True if config was set successfully, false otherwise
   */
  public boolean setConfigXML(Collection<Element> configXML) {
    for (Element element : configXML) {
      String name = element.getName();
      String value;
      Parameter param = Parameter.valueOf(name);

      value = element.getAttributeValue("value");
      if (value == null || value.isEmpty()) {
        /* Backwards compatability: renamed parameters */
        value = element.getText();
      }

      Class<?> paramClass = parameters.get(param).getClass();
      if (paramClass == Double.class) {
        parameters.put(param, new Double(value));
      } else if (paramClass == Boolean.class) {
        parameters.put(param, Boolean.parseBoolean(value));
      } else if (paramClass == Integer.class) {
        parameters.put(param, Integer.parseInt(value));
      } else if (paramClass == String.class) {
        parameters.put(param, String.valueOf(value));
      } else {
        logger.fatal("Unsupported class type: " + paramClass);
      }
    }
    notifySettingsChanged();
    return true;
  }
}

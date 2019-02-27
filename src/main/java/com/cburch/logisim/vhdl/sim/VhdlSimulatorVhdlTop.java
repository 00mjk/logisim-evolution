/*******************************************************************************
 * This file is part of logisim-evolution.
 *
 *   logisim-evolution is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   logisim-evolution is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with logisim-evolution.  If not, see <http://www.gnu.org/licenses/>.
 *
 *   Original code by Carl Burch (http://www.cburch.com), 2011.
 *   Subsequent modifications by :
 *     + Haute École Spécialisée Bernoise
 *       http://www.bfh.ch
 *     + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *       http://hepia.hesge.ch/
 *     + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *       http://www.heig-vd.ch/
 *   The project is currently maintained by :
 *     + REDS Institute - HEIG-VD
 *       Yverdon-les-Bains, Switzerland
 *       http://reds.heig-vd.ch
 *******************************************************************************/

package com.cburch.logisim.vhdl.sim;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.util.FileUtil;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.vhdl.base.VhdlContent;
import com.cburch.logisim.vhdl.base.VhdlEntity;
import com.cburch.logisim.vhdl.base.VhdlEntityAttributes;
import com.cburch.logisim.vhdl.base.VhdlParser;

/**
 * Generates a simulation top file. This file contains all the interfaces to the
 * entities (in and out pins) so the simulation is run on a single top
 * component. It allows us to have only one instance of Questasim running.
 *
 * @author christian.mueller@heig-vd.ch
 */
public class VhdlSimulatorVhdlTop {

	final static Logger logger = LoggerFactory
			.getLogger(VhdlSimulatorVhdlTop.class);

	private boolean valid = false;
	private VhdlSimulatorNew vhdlSimulator;

	VhdlSimulatorVhdlTop(VhdlSimulatorNew vs) {
		vhdlSimulator = vs;
	}

	public void fireInvalidated() {
		valid = false;
	}

	public void generate() {

		/* Do not generate if file is already valid */
		if (valid)
			return;

		StringBuilder ports = new StringBuilder();
		ports.append("Autogenerated by logisim --");
		ports.append(System.getProperty("line.separator"));

		StringBuilder components = new StringBuilder();
		components.append("Autogenerated by logisim --");
		components.append(System.getProperty("line.separator"));

		StringBuilder map = new StringBuilder();
		map.append("Autogenerated by logisim --");
		map.append(System.getProperty("line.separator"));

		Boolean firstPort = true, firstComp = true, firstMap = true;

		/* For each vhdl entity */
		for (Component comp : VhdlSimulatorNew.getVhdlComponents(vhdlSimulator
				.getProject().getCircuitState())) {
			if (comp.getFactory().getClass().equals(VhdlEntity.class)) {

				InstanceState state = vhdlSimulator.getProject()
						.getCircuitState().getInstanceState(comp);

				VhdlContent content = ((VhdlEntityAttributes)state.getAttributeSet()).getContent();

				String vhdlEntityName = comp.getFactory().getHDLTopName(
						state.getInstance().getAttributeSet());

				/*
				 * Create ports
				 */
				for (VhdlParser.PortDescription port : content.getPorts()) {

					if (!firstPort) {
						ports.append(";");
						ports.append(System.getProperty("line.separator"));
					} else {
						firstPort = false;
					}

					String portName = vhdlEntityName + "_" + port.getName();
					ports.append("		" + portName + " : " + port.getVhdlType()
							+ " std_logic");

					int width = port.getWidth().getWidth();
					if (width > 1) {
						ports.append("_vector(" + (width - 1) + " downto 0)");
					}
				}

				/*
				 * Create components
				 */
				components.append("	component " + vhdlEntityName);
				components.append(System.getProperty("line.separator"));

				components.append("		port (");
				components.append(System.getProperty("line.separator"));

				firstComp = true;
				for (VhdlParser.PortDescription port : content.getPorts()) {
					if (!firstComp) {
						components.append(";");
						components.append(System.getProperty("line.separator"));
					} else
						firstComp = false;

					components.append("			" + port.getName() + " : "
							+ port.getVhdlType() + " std_logic");

					int width = port.getWidth().getWidth();
					if (width > 1) {
						components.append("_vector(" + (width - 1)
								+ " downto 0)");
					}
				}

				components.append(System.getProperty("line.separator"));
				components.append("		);");
				components.append(System.getProperty("line.separator"));

				components.append("	end component ;");
				components.append(System.getProperty("line.separator"));

				components.append("	");
				components.append(System.getProperty("line.separator"));

				/*
				 * Create port map
				 */
				map.append("	" + vhdlEntityName + "_map : " + vhdlEntityName
						+ " port map (");
				map.append(System.getProperty("line.separator"));

				firstMap = true;
				for (VhdlParser.PortDescription port : content.getPorts()) {

					if (!firstMap) {
						map.append(",");
						map.append(System.getProperty("line.separator"));
					} else
						firstMap = false;

					map.append("		" + port.getName() + " => "
							+ vhdlEntityName + "_" + port.getName());
				}
				map.append(System.getProperty("line.separator"));
				map.append("	);");
				map.append(System.getProperty("line.separator"));
				map.append("	");
				map.append(System.getProperty("line.separator"));

			}

		}

		ports.append(System.getProperty("line.separator"));
		ports.append("		---------------------------");
		ports.append(System.getProperty("line.separator"));

		components.append("	---------------------------");
		components.append(System.getProperty("line.separator"));

		map.append("	---------------------------");
		map.append(System.getProperty("line.separator"));

		/*
		 * Replace template blocks by generated datas
		 */
		String template;
		try {
			template = new String(
					FileUtil.getBytes(this.getClass()
							.getResourceAsStream(
									VhdlSimulatorNew.VHDL_TEMPLATES_PATH
											+ "top_sim.templ")));
		} catch (IOException e) {
			logger.error("Could not read template : {}", e.getMessage());
			return;
		}

		template = template.replaceAll("%date%",
				LocaleManager.parserSDF.format(new Date()));
		template = template.replaceAll("%ports%", ports.toString());
		template = template.replaceAll("%components%", components.toString());
		template = template.replaceAll("%map%", map.toString());

		PrintWriter writer;
		try {
			writer = new PrintWriter(VhdlSimulatorNew.SIM_SRC_PATH
					+ VhdlSimulatorNew.SIM_TOP_FILENAME, "UTF-8");
			writer.print(template);
			writer.close();
		} catch (FileNotFoundException e) {
			logger.error("Could not create top_sim file : {}", e.getMessage());
			e.printStackTrace();
			return;
		} catch (UnsupportedEncodingException e) {
			logger.error("Could not create top_sim file : {}", e.getMessage());
			e.printStackTrace();
			return;
		}

		valid = true;
	}

}

/*
 * File   : $Source: /alkacon/cvs/opencms/src-setup/org/opencms/setup/comptest/CmsSetupTestServletContainer.java,v $
 * Date   : $Date: 2007/11/26 11:51:08 $
 * Version: $Revision: 1.3 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) 2002 - 2007 Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.setup.comptest;

import org.opencms.setup.CmsSetupBean;

import javax.servlet.ServletConfig;

/**
 * Tests the servlet container.<p>
 * 
 * @author Michael Moossen
 * 
 * @version $Revision: 1.3 $ 
 * 
 * @since 6.1.8 
 */
public class CmsSetupTestServletContainer implements I_CmsSetupTest {

    /** The test name. */
    public static final String TEST_NAME = "Servlet Container";

    /**
     * @see org.opencms.setup.comptest.I_CmsSetupTest#getName()
     */
    public String getName() {

        return TEST_NAME;
    }

    /**
     * @see org.opencms.setup.comptest.I_CmsSetupTest#execute(org.opencms.setup.CmsSetupBean)
     */
    public CmsSetupTestResult execute(CmsSetupBean setupBean) {

        CmsSetupTestResult testResult = new CmsSetupTestResult(this);

        String[] supportedContainers = {
            "Apache Tomcat/4.1",
            "Apache Tomcat/5",
            "Apache Tomcat/6",
            "WebLogic Server 9",
            "Resin/3"};

        String[] unsupportedContainers = {"Tomcat Web Server/3", "Apache Tomcat/4.0", "Resin/2"};

        String[] unsupportedServletContainerInfo = {
            "Tomcat 3.x is no longer supported. Please use at least Tomcat 4.1.x instead.",
            "Tomcat 4.0.x is no longer supported. Please use at least Tomcat 4.1.x instead.",
            "The OpenCms JSP integration does not work with Resin 2.x. Please use Resin 3.x instead."};

        ServletConfig config = setupBean.getServletConfig();
        String servletContainer = config.getServletContext().getServerInfo();
        int supportedServletContainer = hasSupportedServletContainer(servletContainer, supportedContainers);
        int unsupportedServletContainer = unsupportedServletContainer(servletContainer, unsupportedContainers);

        testResult.setResult(servletContainer);

        if (unsupportedServletContainer > -1) {
            testResult.setRed();
            testResult.setInfo(unsupportedServletContainerInfo[unsupportedServletContainer]);
            testResult.setHelp("This servlet container does not work with OpenCms. Even though OpenCms is fully standards compliant, "
                + "the standard leaves some 'grey' (i.e. undefined) areas. "
                + "Please consider using another, supported servlet container.");
        } else if (supportedServletContainer < 0) {
            testResult.setYellow();
            testResult.setHelp("This servlet container has not been tested with OpenCms. Please consider using another, supported servlet container.");
        } else if (supportedServletContainer == 4) {
            // resin
            testResult.setInfo("Please be sure that during the Setup Wizard, the web application auto-redeployment feature is deactivated. One way to achieve this, is to set the '<code>dependency-check-interval</code>' option in your <code>resin.conf</code> configuration file to <code>-1</code> or something big like <code>2000s</code>.");
        } else {
            testResult.setGreen();
        }
        return testResult;
    }

    /** 
     * Checks if the used servlet container is part of the servlet containers OpenCms supports.<p>
     * 
     * @param thisContainer The servlet container in use
     * @param supportedContainers All known servlet containers OpenCms supports
     * 
     * @return true if this container is supported, false if it was not found in the list
     */
    private int hasSupportedServletContainer(String thisContainer, String[] supportedContainers) {

        for (int i = 0; i < supportedContainers.length; i++) {
            if (thisContainer.indexOf(supportedContainers[i]) >= 0) {
                return i;
            }
        }
        return -1;
    }

    /** 
     * Checks if the used servlet container is part of the servlet containers OpenCms
     * does NOT support.<p>
     * 
     * @param thisContainer the servlet container in use
     * @param unsupportedContainers all known servlet containers OpenCms does NOT support
     * 
     * @return the container id or -1 if the container is not supported
     */
    private int unsupportedServletContainer(String thisContainer, String[] unsupportedContainers) {

        for (int i = 0; i < unsupportedContainers.length; i++) {
            if (thisContainer.indexOf(unsupportedContainers[i]) >= 0) {
                return i;
            }
        }
        return -1;
    }
}

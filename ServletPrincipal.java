/*
 * Creado el 20/12/2012
 *
 * TODO Para cambiar la plantilla de este archivo generado, vaya a
 * Ventana - Preferencias - Java - Estilo de código - Plantillas de código
 */
package com.iusacell.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Date;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.WebApplicationContextUtils;

import com.iusacell.configuracion.PrintLogMonitoreo;
import com.iusacell.controllers.AnalizaLineas;
import com.iusacell.model.vo.DatosSoaPortablidadVO;

/**
 * @author AVAZQUEZVE
 * 
 * TODO Para cambiar la plantilla de este comentario generado, vaya a67890'j
 * Ventana - Preferencias - Java - Estilo de código - Plantillas de código
 */
public class ServletPrincipal extends HttpServlet {
	private static Logger log = Logger.getLogger(ServletPrincipal.class);

	protected void doGet(HttpServletRequest request,
			HttpServletResponse response) throws ServletException, IOException {
	    PrintLogMonitoreo logger = new PrintLogMonitoreo(ServletPrincipal.class,"ServletPrincipal");
		// http://localhost:9080/ReintentoPortabilidad/servlet/ServletPrincipal?dn=3319444780
		// http://192.168.190.62:9080/ReintentoPortabilidad/servlet/ServletPrincipal?dn=4441989003
		// http://test.iusacell.com.mx/ReintentoPortabilidad/ServletPrincipal?dn=5541127091

		Writer writer = writer = response.getWriter();
		PrintWriter out = new PrintWriter(writer);
		DatosSoaPortablidadVO baja = null;		
		Date fecha = null;
		String port_id = "";
		String dn = "";
		/* Recepcion de parametros */
		if (request.getParameter("dn") != null
				&& request.getParameter("idColas") != null//				
				&& request.getParameter("portId") != null
				&& request.getParameter("donadorId") != null
				&& request.getParameter("receptorId") != null
				&& request.getParameter("motivoCambioId") != null) {				
				fecha = new Date();
		
			baja = new DatosSoaPortablidadVO(request.getParameter("dn"),
					request.getParameter("portId"), 
					request.getParameter("idColas"),
					fecha,
					Integer.parseInt(request.getParameter("donadorId")),
					Integer.parseInt(request.getParameter("receptorId")),
					Short.parseShort(request.getParameter("motivoCambioId")));
			
			port_id = request.getParameter("portId");
			dn = request.getParameter("dn");
			
			MDC.put("portID", port_id);
			MDC.put("DN", dn);
			MDC.put("mod", "DONACIONES");
			
			log.info("DN recibido:" + baja.getDnProvisional());
			log.info("IdColas recibido:" + baja.getLogcolasId());
			log.info("Fecha recibida:" + baja.getFechaInsert());
			log.info("PorId recibido:" + baja.getPortId());
			log.info("DonadorId recibido:" + baja.getDonadorId());
			log.info("ReceptorId recibido:" + baja.getReceptorId());
			log.info("MotivoCambioId recibido:" + baja.getMotivoCambioId());

			response.setContentType("text/html; charset=windows-1252");
			out.println("<html>");
			out.println("<body>");
			out.println("PROBANDO   3 - 6 - 8");
			out.println("</body></html>");

			WebApplicationContext context = WebApplicationContextUtils
					.getWebApplicationContext(this.getServletContext());

			AnalizaLineas analizaLineas = (AnalizaLineas) context
					.getBean("analizaLineas");
			ServletContext ctx = getServletContext();
			HashMap map = (HashMap) ctx.getAttribute("configuracion");
			analizaLineas.analizaLineasDonacion(baja, map);
			
			logger.fillLog("Finaliza ServletPrincipal analizaLineas.analizaLineasDonacion");
			
			MDC.remove("portID");
		    MDC.remove("DN");
		    MDC.remove("mod");
		}
	}
}

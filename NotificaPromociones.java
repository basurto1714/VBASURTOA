/*
 * Created on 10/12/2015
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package com.iusacell.controllers;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.xml.rpc.ServiceException;

import mx.com.iusacell.prepago.sconsultasprepago.gsm.impl.SConsultasPrepagoGSMImplFalta;

import org.apache.log4j.Logger;

import com.iusacell.model.dao.IBitacoraDAO;
import com.iusacell.model.dao.ISoaPortabilidad;
import com.iusacell.model.service.ConsultasPrepagoGSM;
import com.iusacell.model.service.CyCService;
import com.iusacell.model.service.INumerosServicio;
import com.iusacell.model.service.NotificadorPromocionesPrepago;
import com.iusacell.model.utils.Constantes;
import com.iusacell.model.utils.ILlenadoObjetos;
import com.iusacell.model.utils.Utils;
import com.iusacell.model.vo.BitacoraReprocesoDetalleVO;
import com.iusacell.model.vo.BitacoraReprocesoVO;
import com.iusacell.model.vo.ParametroValidacionPromocionVO;
import com.iusacell.model.vo.PromocionVO;
import com.iusacell.model.vo.RecepcionVO;
import com.mindbits.sir.model.vo.number.CellNumberVO;

/**
 * @author osanchezh
 * 
 * TODO To change the template for this generated type comment go to Window -
 * Preferences - Java - Code Style - Code Templates
 */
public class NotificaPromociones {

	private static Logger log = Logger.getLogger(NotificaPromociones.class);
	
	private IBitacoraDAO bitacoraReproceso;

	private ISoaPortabilidad soaPortabilidad;

	private CyCService cycService;

	private INumerosServicio compNumeros;

	private ILlenadoObjetos llenaObjetos;
	
	
	public boolean notificaPromociones(String mdn, String portId, int idLinea, String secuencia, boolean banderaReproceso) {		
		String mdn_provisional = null;
		CellNumberVO cellNumberVOMDN = null;		
		int tipo_pago_provisional = 0;
		int idOperador = 0;
		BitacoraReprocesoVO bitacoraReprocesoVO = null;
		boolean bandera = false;
		BigDecimal portidDecimal = new BigDecimal(portId);
		
		try {
			log.info("Entro a notificaPromocion mdn: " + mdn + " portId: " + portId + " idLinea: " + idLinea);
			bitacoraReprocesoVO = new BitacoraReprocesoVO();
			mdn_provisional = soaPortabilidad.getMDNProvisional(portidDecimal, mdn);			
			int idLinea2 = cycService.consultaIdLinea(mdn, secuencia);
			log.info("IdLinea1: "+ idLinea);
			log.info("IdLinea2: "+ idLinea2);
			if(idLinea2 > 0){
				idLinea = idLinea2;
			}
			
			//Reintentamos busqueda de idLinea en el nuevo servicio PrepagoGSM
			if(idLinea == 0){
				log.info("Reintentamos buscar la linea en el servicio de PrepagoGSM");
				mx.com.iusacell.telco.comun.Cliente infoCliente = ConsultasPrepagoGSM.getInstance().consultaInformacionPrepago(mdn);
				if(infoCliente != null){
					idLinea = Integer.parseInt(infoCliente.getCuenta().getLinea().getId());
				}else{
					log.info("No se encuentra el idLinea del DN:"+ mdn);
					idLinea = 0;
				}
			}
			
			cellNumberVOMDN = compNumeros.getDatosNumsRecepcion(mdn, secuencia);
			if( null == cellNumberVOMDN ){
				log.error( "El método getDatosNumsRecepcion no ha regresado valores y es requerido para continuar con el proceso." );
				return false;
			}
			tipo_pago_provisional = compNumeros.getTipoPago(cellNumberVOMDN.getSistFacturaVO());
			log.info("tipo_pago_provisional recuperado, segunda vez :" + tipo_pago_provisional);
			idOperador = cellNumberVOMDN.getOperadorVO().getIntIdOperador();
			
			bitacoraReprocesoVO.setReprocesoId(secuencia);
	        bitacoraReprocesoVO.setPortId(portId);
	        bitacoraReprocesoVO.setDn(mdn);
	        bitacoraReprocesoVO.setDnProvisional(mdn_provisional);
	        bitacoraReprocesoVO.setTipoPago(tipo_pago_provisional);
	        bitacoraReprocesoVO.setIdOperador(idOperador);
		    bitacoraReprocesoVO.setFechaPortado( soaPortabilidad.getFechaPortado(new BigDecimal(bitacoraReprocesoVO.getPortId()),bitacoraReprocesoVO.getDn()) );
		    bitacoraReprocesoVO = llenaObjetos.llenaObjetoBitacora(bitacoraReprocesoVO);
		    
		    if(tipo_pago_provisional == 2 || tipo_pago_provisional == 3){
	    	    log.info("Solo tipo_pago 2 y 3");
		    	bitacoraReprocesoVO = llenaObjetos.llenaObjetoHibPosBitacora(cellNumberVOMDN,bitacoraReprocesoVO);
	    	}
		    
            //Si tipo pago provisional = 1 o 3 y idlinea = 0 no continue
		    if(tipo_pago_provisional == 1 || tipo_pago_provisional == 3 && idLinea == 0){
		    	return false;
		    }
		    
		    List resultadoPromociones = aplicaPromociones( secuencia, mdn, idLinea, bitacoraReprocesoVO, tipo_pago_provisional, idOperador, banderaReproceso );
		    
		    
		    int contadorPromoExito = 0;
		    int totalPromoNotificadas = resultadoPromociones.size();
		    log.info( "RESUMEN GENERAL DE PROMOCIONES PARA EL MDN: " + mdn );
		    for (Iterator iter = resultadoPromociones.iterator(); iter.hasNext();) {		    	
				HashMap resumenPromociones = (HashMap) iter.next();
				log.info( "PROMOCIÓN APLICADA: " + resumenPromociones.get("APLICADA") );
				log.info( "DESCRIPCION PROMOCIÓN: " + resumenPromociones.get("DESC") );
				log.info( "EXITO EN LA APLICACIÓN: " + resumenPromociones.get("EXITO") );
				if( Boolean.valueOf(resumenPromociones.get("EXITO").toString()).booleanValue()){
					contadorPromoExito += 1;
				}
			}
		    log.info("Total de promociones para el dn: " + mdn + "," + totalPromoNotificadas);
		    log.info("Total de promociones exitosas para el dn: " + mdn + "," + contadorPromoExito);
		    if(contadorPromoExito == totalPromoNotificadas){
		    	log.info( "Actualizar recepción a estatus 1 - EXITO " );
		    	bitacoraReproceso.updateStatusRecepcion(bitacoraReprocesoVO);
		    	bandera = true;
		    }		    
		} catch (Exception e) {
			log.error( "Error en el proceso de aplicación de promociones", e );
		}
		return bandera;
	}
	
	private List aplicaPromociones(String secuencia, String mdn, int idLinea, BitacoraReprocesoVO bitacoraReprocesoVO, int tipo_pago_provisional, int idOperador, boolean banderaReproceso) {
		log.info( "Aplicar promociones para el MDN: " + mdn );
		List resultList = new ArrayList();
		HashMap respuesta = null;
		RecepcionVO recepcion = new RecepcionVO();
		recepcion.setBitacoraReprocesoVO( bitacoraReprocesoVO );
		recepcion.setIdLinea( idLinea );
		recepcion.setMdn( mdn );
		recepcion.setSecuencia( secuencia );
		recepcion.setTipoPagoProvisional( tipo_pago_provisional );
		recepcion.setIdOperador( idOperador );
		
		//capos para valdación de la promoción.
		recepcion.setPortId( bitacoraReprocesoVO.getPortId() );
		recepcion.setIdPlan( Utils.isEmptyNumber( bitacoraReprocesoVO.getTmcode() ) );
		recepcion.setPlazo( Utils.isEmptyNumber(bitacoraReprocesoVO.getPlazo() ) ) ;
		recepcion.setFechaActivacion( bitacoraReprocesoVO.getFechaActivacion() );
		recepcion.setFechaPortado( bitacoraReprocesoVO.getFechaPortado());
		recepcion.setTipoClienteBSCS( bitacoraReprocesoVO.getTipoCliente());
		
		log.info("***Valores asignados a validar***");
		log.info("PortId:" + recepcion.getPortId());
		log.info("IdPlan:" + recepcion.getIdPlan());
		log.info("Plazo:" + recepcion.getPlazo());
		log.info("FechaActivacion:" + recepcion.getFechaActivacion());
		log.info("FechaPortado:" + recepcion.getFechaPortado());
		log.info("TipoClienteBSCS:" + recepcion.getTipoClienteBSCS());
		log.info("IdLinea:" + recepcion.getIdLinea());
		
		boolean respuestaPromociones = false;
		//1 - Obtener la lista de promociones que aplican para la recepción entrante
		List promociones = bitacoraReproceso.getPromociones( recepcion );
		if( null != promociones && !promociones.isEmpty() ){
			//2 - Si aplican promociones para la recepción, aplicamos las validaciones
			log.info( "Existen {"+ promociones.size() +"} promociones posibles a aplicar para esta recepción:[" + mdn + "]" );
			for (Iterator iter = promociones.iterator(); iter.hasNext();) {
				PromocionVO promocion = (PromocionVO) iter.next();
				log.info( "Realizar validaciones para la promoción: [" + promocion.getId() + "] : {" + promocion.getDescripcion() + "}" );
				if( isPromocionValida( promocion, recepcion ) ){
					//3 - La promoción es válida para la recepción, aplicar promoción
					log.info( "La promoción es válida para la recepción, aplicar promoción");
					respuesta = notificaPromocion( promocion, recepcion, banderaReproceso);
					resultList.add( respuesta );
					if(respuesta.containsKey("DESACTIVACION")){
						if( Boolean.valueOf(respuesta.get("DESACTIVACION").toString()).booleanValue() ){
							break;
						}
					}
				}else{
					//3 - La promoción NO es válida para la recepción. Ignorar esta promoción
					log.info( "La promoción NO es válida para la recepción. Ignorar esta promoción");
					respuesta = new HashMap();
					respuesta.put( "APLICADA", new Boolean(false) );
					respuesta.put( "DESC", "La promoción {"+ promocion.getId() +"} no es valida para esta recepción: " + mdn );
					respuesta.put( "EXITO", new Boolean(true) );
					resultList.add( respuesta );
				}
			}
		}else{
			//2 - No existen promociones validas para esta recepcion, analizar que regresar
			respuesta = new HashMap();
			respuesta.put( "APLICADA", new Boolean(false) );
			respuesta.put( "DESC", "No existen promociones a aplicar para esta recepción: " + mdn );
			respuesta.put( "EXITO", new Boolean(true) );
			resultList.add( respuesta );
			log.info( "No existen promociones que apliquen a esta receción.");
			respuestaPromociones = true;
		}
		return resultList;
	}
	
	
	private HashMap notificaPromocion(PromocionVO promocion, RecepcionVO recepcion, boolean banderaReproceso) {
		log.info("Entro a notificacion de promociones");
		log.info("secuencia = " + recepcion.getSecuencia() + "  mdn = " + recepcion.getMdn() + "  idLinea = " + recepcion.getIdLinea());
		HashMap promocionAplicada = new HashMap();
		if( promocion.getIdLinea().intValue() ==  Constantes.LINEA_PREPAGO && banderaReproceso == false && recepcion.getIdLinea() != 0){
			promocionAplicada = notificaDesactivacionPromocionesPrepago( promocion, recepcion );
		} else if( promocion.getIdLinea().intValue() ==  Constantes.LINEA_PREPAGO && banderaReproceso == true && recepcion.getIdLinea() != 0){
			promocionAplicada = notificaPromocionesPrepago( promocion, recepcion );
		} else if( promocion.getIdLinea().intValue() ==  Constantes.LINEA_POSTPAGO){
			promocionAplicada = notificaPromocionesPospagoHibrido( promocion, recepcion, banderaReproceso);
		}else if( promocion.getIdLinea().intValue() ==  Constantes.LINEA_HIBRIDA && recepcion.getIdLinea() != 0){
			promocionAplicada = notificaPromocionesPospagoHibrido( promocion, recepcion, banderaReproceso);
		}
		return promocionAplicada;
	}
	
	private boolean isPromocionValida(PromocionVO promocion, RecepcionVO recepcion) {
		log.info( "Obtener los parámetros a validar de la promción: " + promocion.getId() );
		List parametros = bitacoraReproceso.getParametrosPromocion( promocion );
		boolean isValid = false;
		if( null != parametros && !parametros.isEmpty() ){
			log.info( "Existen ["+parametros.size()+"] condiciones a validar, para ésta promoción {"+promocion.getId()+"}" );
			for (Iterator iter = parametros.iterator(); iter.hasNext();) {

					ParametroValidacionPromocionVO validacion = (ParametroValidacionPromocionVO) iter.next();
					try {
						Class[] clss = { List.class };
						Method methodValidation = recepcion.getClass().getMethod( validacion.getValidacion(), clss);
						Object[] params = {validacion.getParametros()};
						Boolean resultm = (Boolean) methodValidation.invoke( recepcion, params );
						if( resultm.booleanValue() ){
							log.info( "La validación del método {" + validacion.getValidacion() + "} es correcta. seguir" );
							isValid = true;
						}else{
							log.info( "La validación del método {" + validacion.getValidacion() + "} no es satisfactoria. Detener validación" );
							isValid = false;
							break;
						}
					} catch (Exception e1) {
						e1.printStackTrace();
						isValid = false;
						break;
					}
			}
		}else{
			//No existen parametros a validar para la promoción, es válida por default
			log.info( "No existen parametros a validar para la promoción, es válida por default." );
			return true;
		}
		return isValid;
	}
	
	private HashMap notificaPromocionesPrepago( PromocionVO promocion, RecepcionVO recepcion  ) {
		HashMap respuesta = new HashMap();
		log.info("Entro a notificacion de promociones");
		String secuencia = recepcion.getSecuencia();
		String mdn = recepcion.getMdn();
		int idLinea = recepcion.getIdLinea();
		BitacoraReprocesoVO bitacoraReprocesoVO = recepcion.getBitacoraReprocesoVO();
		log.info("secuencia = " + secuencia + "  mdn = " + mdn + "  idLinea = " + idLinea);

		mx.com.iusacell.comun.xsd.Cliente cliente = new mx.com.iusacell.comun.xsd.Cliente();
		mx.com.iusacell.comun.xsd.Cuenta cuenta = new mx.com.iusacell.comun.xsd.Cuenta();
		mx.com.iusacell.comun.xsd.Linea linea = new mx.com.iusacell.comun.xsd.Linea();
		mx.com.iusacell.comun.xsd.Plan plan = new mx.com.iusacell.comun.xsd.Plan();	
		mx.com.iusacell.comun.xsd.Terminal terminal = new mx.com.iusacell.comun.xsd.Terminal();
		mx.com.iusacell.comun.xsd.Vendedor vendedor = new mx.com.iusacell.comun.xsd.Vendedor();		
		boolean resultPromo = false;
		
		try{
			log.info("CONSULTAR INFORMACIÓN");
			mx.com.iusacell.telco.comun.Cliente infoCliente = ConsultasPrepagoGSM.getInstance().consultaInformacionPrepago(mdn);
			if( null == infoCliente.getCuenta() 
					|| null == infoCliente.getCuenta().getLinea() 
					|| null == infoCliente.getCuenta().getLinea().getTerminal()){
				throw new NullPointerException("Alguno de los valores requeridos del servicio es nulo.");
			}
			
			
			linea.setId(""+idLinea);
			log.info("setId:"+idLinea);
			linea.setDn(mdn);			
			log.info("setDN:" + mdn);
			linea.setIdCiudad(Utils.isEmptyNumber(infoCliente.getCuenta().getLinea().getIdCiudad()));/*PARECE OPCIONAL*/
			log.info("setIdCiudad:" + linea.getIdCiudad());
			linea.setIdOperador(new Short(infoCliente.getCuenta().getLinea().getIdOperador().toString()));
			log.info("setIdOperador:" + linea.getIdOperador());			
			linea.setInstalacion(Utils.isEmpty(infoCliente.getCuenta().getLinea().getInstalacion()));
			log.info("setInstalacion:" + linea.getInstalacion());
			linea.setRegion( Utils.isEmpty( infoCliente.getCuenta().getLinea().getRegion()) );
			log.info("setRegion:" + linea.getRegion());
			linea.setStatus(Utils.isEmpty( infoCliente.getCuenta().getLinea().getStatus()));
			log.info("setStatus:" + linea.getStatus());
			linea.setTecnologia(Utils.isEmpty( infoCliente.getCuenta().getLinea().getTecnologia()));
			log.info("setTecnologia:" + linea.getTecnologia());
			linea.setTipo( new Integer(1) );/*PREPAGO*/
			log.info("setTipo:"+1);
			linea.setMotivoActivacion( new Short("0"));/*PARECE OPCIONAL*/
			log.info("setMotivoActivacion:"+0);	
			linea.setContratacion(Utils.isEmpty(infoCliente.getCuenta().getLinea().getContratacion()));
			log.info("setContratacion:" + linea.getContratacion());
			linea.setVencimiento(Utils.isEmpty(infoCliente.getCuenta().getLinea().getVencimiento()));
			log.info("setVencimiento:" + linea.getVencimiento());			
			terminal.setPropietario( Utils.isEmpty( infoCliente.getCuenta().getLinea().getTerminal().getPropietario()) );
			log.info("Propietario:"+terminal.getPropietario());			
			linea.setTerminal( terminal );			
			plan.setId(Utils.isEmpty(infoCliente.getCuenta().getLinea().getPlan().getId()));
			log.info("Plan id:" + plan.getId());
			plan.setIdPrepago(Utils.isEmpty(infoCliente.getCuenta().getLinea().getPlan().getIdPrepago()));
			log.info("Plan id prepago:" + plan.getIdPrepago());
			linea.setPlan(plan);			
			vendedor.setCanal(Constantes.VACIO);
			vendedor.setPuntoVenta(Constantes.VACIO);
			linea.setVendedor(vendedor);
			cuenta.setLinea(linea);
			cliente.setCuenta( cuenta );
				
			log.info( "## ID PORTABILIDAD: " + bitacoraReprocesoVO.getPortId() );
			log.info( "## DN: " + bitacoraReprocesoVO.getDn() );
			log.info( "## DN PROVISIONAL: " + bitacoraReprocesoVO.getDnProvisional() );
			log.info( "## ID REPROCESO: " + bitacoraReprocesoVO.getReprocesoId() );
			
			resultPromo = invokePromocion(cliente, new Long(promocion.getId().toString()), secuencia);			
			
		} catch (ServiceException e1) {			
			log.error("Error al crear singleton de info prepago.", e1);			
		} catch (SConsultasPrepagoGSMImplFalta e) {
			log.error("Error notificado en cliente ws prepago info.", e);			
		} catch (NumberFormatException e) {
			log.error("Error al crear parametros del ws prepago info.", e);			
		} catch (RemoteException e) {
			log.error("Error al invocar el cliente de info prepago.", e);			
		} catch (Exception e) {
			log.error( "Exception: " + e.getMessage() );
		} finally{			
			respuesta.put( "APLICADA", new Boolean(resultPromo) );
			respuesta.put( "DESC", "Respuesta de notificación de promoción.");
			respuesta.put( "EXITO", new Boolean(resultPromo) );
			
			if(resultPromo == false){
				bitacoraReproceso.updateStatusRecepcionErrorPromocionPrepago(bitacoraReprocesoVO);
			}
		}
		return respuesta;
	}
	
	private HashMap notificaDesactivacionPromocionesPrepago( PromocionVO promocion, RecepcionVO recepcion  ) {
		HashMap respuesta = new HashMap();
		log.info("Entro a notificacion de desactivacion promociones");
		String secuencia = recepcion.getSecuencia();
		String mdn = recepcion.getMdn();
		int idLinea = recepcion.getIdLinea();
		BitacoraReprocesoVO bitacoraReprocesoVO = recepcion.getBitacoraReprocesoVO();
		log.info("secuencia = " + secuencia + "  mdn = " + mdn + "  idLinea = " + idLinea);

		mx.com.iusacell.comun.xsd.Linea linea = new mx.com.iusacell.comun.xsd.Linea();		
		boolean rsEliminaPromo = false;
		String requestXMLData ="";
		try{
			log.info("CONSULTAR INFORMACIÓN");
			mx.com.iusacell.telco.comun.Cliente infoCliente = ConsultasPrepagoGSM.getInstance().consultaInformacionPrepago(mdn);
			if( null == infoCliente.getCuenta() 
					|| null == infoCliente.getCuenta().getLinea() 
					|| null == infoCliente.getCuenta().getLinea().getTerminal()){
				throw new NullPointerException("Alguno de los valores requeridos del servicio es nulo.");
			}
			
			linea.setId(""+idLinea);
			linea.setDn(mdn);
			linea.setIdOperador(new Short(infoCliente.getCuenta().getLinea().getIdOperador().toString()));
			linea.setTipo( new Integer(1) );/*PREPAGO*/			
		    rsEliminaPromo = eliminaPromociones(linea);		    
			requestXMLData = Utils.objectToXML(linea, new Long((1)));
			
		} catch (ServiceException e1) {			
			log.error("Error al crear singleton de info prepago.", e1);			
		} catch (SConsultasPrepagoGSMImplFalta e) {
			log.error("Error notificado en cliente ws prepago info.", e);			
		} catch (NumberFormatException e) {
			log.error("Error al crear parametros del ws prepago info.", e);			
		} catch (RemoteException e) {
			log.error("Error al invocar el cliente de info prepago.", e);			
		} catch (Exception e) {
			log.error( "Exception: " + e.getMessage() );
		} finally{			
			/*Registro en bitacora*/
			BitacoraReprocesoDetalleVO  bitacoraReprocesoDetalleVo = llenaObjetos.llenaObjetoBitacoraRecepcionDetalle(Constantes.ELIMINA_PROMOCIONES, (rsEliminaPromo?Constantes.EXITO:Constantes.ERROR), requestXMLData, secuencia, null);		
			bitacoraReprocesoDetalleVo.setDatosSalida(Constantes.VACIO);
			bitacoraReproceso.setBitacoraRecepcionDetalle(bitacoraReprocesoDetalleVo);
			
			/*Actualizacion de registro*/
			if(rsEliminaPromo == true){
				respuesta.put( "DESC", "La promción no se aplica, pero se notifican desactivación de promociones.");
				log.info("Se actualiza a estatus 5, notifica desactivacion");
				bitacoraReproceso.updateStatusRecepcionPromoEliminadas(bitacoraReprocesoVO);
				log.info("Se ha notificado desactivación de promoción");
			}else{
				respuesta.put( "DESC", "La promción no se aplica, falló al notificar desactivación de promociones.");
				log.info("Se actualiza a estatus 4, fallo en notifica desactivacion");
				bitacoraReproceso.updateStatusRecepcionErrorPromocionPrepago(bitacoraReprocesoVO);				
			}
			respuesta.put( "APLICADA", new Boolean(false) );			
			respuesta.put( "EXITO", new Boolean(false) );
			respuesta.put( "DESACTIVACION", new Boolean(rsEliminaPromo) );
		}
		return respuesta;
	}
	
	
	private HashMap notificaPromocionesPospagoHibrido( PromocionVO promocion, RecepcionVO recepcion, boolean banderaReproceso) {
		log.info("Entro a notificacion de promociones");
		HashMap respuesta = new HashMap();
		String secuencia = recepcion.getSecuencia();
		String mdn = recepcion.getMdn();
		int idLinea = recepcion.getIdLinea();
		BitacoraReprocesoVO bitacoraReprocesoVO = recepcion.getBitacoraReprocesoVO();
		int tipo_pago_provisional = recepcion.getTipoPagoProvisional();
		
		log.info("secuencia = " + secuencia + "  mdn = " + mdn + "  idLinea = " + idLinea);

		mx.com.iusacell.comun.xsd.Cliente cliente = new mx.com.iusacell.comun.xsd.Cliente();
		mx.com.iusacell.comun.xsd.Cuenta cuenta = new mx.com.iusacell.comun.xsd.Cuenta();
		mx.com.iusacell.comun.xsd.Linea linea = new mx.com.iusacell.comun.xsd.Linea();
		mx.com.iusacell.comun.xsd.Plan plan = new mx.com.iusacell.comun.xsd.Plan();	
		mx.com.iusacell.comun.xsd.Terminal terminal = new mx.com.iusacell.comun.xsd.Terminal();
		mx.com.iusacell.comun.xsd.Vendedor vendedor = new mx.com.iusacell.comun.xsd.Vendedor();
		mx.com.iusacell.comun.xsd.Pago pago = new mx.com.iusacell.comun.xsd.Pago();
		boolean banderaGeneral = false;
		
		try{			
			/*ID CLIENTE*/
			cliente.setId(bitacoraReprocesoVO.getCustomerId());
			log.info("cliente.setId:" + cliente.getId());
			/*TIPO CLIENTE*/
			cliente.setTipo(bitacoraReprocesoVO.getTipoCliente());
			log.info("cliente.setTipo:" + cliente.getTipo());
			/*CICLO FACTURACION CLIENTE*/
			cliente.setCicloFacturacion(bitacoraReprocesoVO.getCicloFacturacion());
			log.info("cliente.setCicloFacturacion:" + cliente.getCicloFacturacion());
			/*TIPO CTA*/
			cuenta.setTipo(bitacoraReprocesoVO.getTipoCuenta());
			log.info("cuenta.setTipo:" + cuenta.getTipo());
			
			/*LINEA*/
		    linea.setId(idLinea + "");
			log.info("setId:" + linea.getId());			
			linea.setDn(mdn);			
			log.info("setDN:" + mdn);
			linea.setIdCiudad(Utils.isEmptyNumber(new Integer(bitacoraReprocesoVO.getCiudad())));/*PARECE OPCIONAL*/
			log.info("setIdCiudad:" + linea.getIdCiudad());
			linea.setIdOperador(new Short(bitacoraReprocesoVO.getIdOperador()+""));
			log.info("setIdOperador:" + linea.getIdOperador());			
			Calendar calActivacion = Calendar.getInstance();
			calActivacion.setTime(bitacoraReprocesoVO.getFechaActivacion());
			linea.setInstalacion(Utils.isEmpty(calActivacion));
			log.info("setInstalacion:" + linea.getInstalacion());
			linea.setRegion( Utils.isEmpty(new Integer(bitacoraReprocesoVO.getRegion()) ) );
			log.info("setRegion:" + linea.getRegion());
			linea.setStatus(Utils.isEmpty(new Integer( bitacoraReprocesoVO.getStatusNumero())));
			log.info("setStatus:" + linea.getStatus());
			linea.setTecnologia(Utils.isEmpty(new Integer( bitacoraReprocesoVO.getTecnologia())));
			log.info("setTecnologia:" + linea.getTecnologia());
			linea.setTipo( new Integer(tipo_pago_provisional) );
			log.info("setTipo:"+tipo_pago_provisional);
			linea.setMotivoActivacion( new Short("0"));/*PARECE OPCIONAL*/
			log.info("setMotivoActivacion:"+0);			
			linea.setContrato(new Integer(bitacoraReprocesoVO.getCoId() + ""));
			log.info("setContrato:" + linea.getContrato());			
			linea.setPlazo(new Integer(bitacoraReprocesoVO.getPlazo()));
			log.info("setPlazo:" + linea.getPlazo());			
			linea.setContratacion(Utils.isEmpty(calActivacion));
			log.info("setContratacion:" + linea.getContratacion());			
			Calendar calVencimiento = Calendar.getInstance();
			calVencimiento.setTime(Utils.formateaFechaDate(bitacoraReprocesoVO.getVencimiento()));
			linea.setVencimiento(Utils.isEmpty(calVencimiento));
			log.info("setVencimiento:" + linea.getVencimiento());			
			
			/*PLAN*/
			plan.setId(Utils.isEmpty(new Integer(bitacoraReprocesoVO.getTmcode())));
			log.info("Plan id:" + plan.getId());
			linea.setPlan(plan);	
			
			/*TERMINAL*/
			terminal.setPropietario(Utils.isEmpty(new Integer(0)));
			log.info("Propietario:"+terminal.getPropietario());			
			linea.setTerminal( terminal );
			
			/*VENDEDOR*/			
			vendedor.setCanal(Constantes.VACIO);
			vendedor.setPuntoVenta(Constantes.VACIO);
			linea.setVendedor(vendedor);
			
			/*CUENTA*/			
			cuenta.setLinea(linea);
			
			/*PAGOS*/
			pago.setFormaPago( bitacoraReprocesoVO.getIdTipoPago() );
			log.info("pago.setFormaPago:" + pago.getFormaPago());
			mx.com.iusacell.comun.xsd.Pago[] pagos = new mx.com.iusacell.comun.xsd.Pago[]{pago};
			cuenta.setPagos(pagos);
			
			cliente.setCuenta( cuenta );
			
			log.info( "## ID PORTABILIDAD: " + bitacoraReprocesoVO.getPortId() );
			log.info( "## DN: " + bitacoraReprocesoVO.getDn() );
			log.info( "## DN PROVISIONAL: " + bitacoraReprocesoVO.getDnProvisional() );
			log.info( "## ID REPROCESO: " + bitacoraReprocesoVO.getReprocesoId() );
			
			banderaGeneral = invokePromocion(cliente, new Long(promocion.getId().toString()), secuencia);
			
		} catch (NumberFormatException e) {
			log.error("Error al crear parametros del ws prepago info.", e);			
		} catch (Exception e) {
			log.error( "Exception: " + e.getMessage() );
		} finally{
			respuesta.put( "APLICADA", new Boolean(banderaGeneral) );
			respuesta.put( "DESC", "Respuesta de notificación de promoción.");
			respuesta.put( "EXITO", new Boolean(banderaGeneral) );
			
			if(banderaReproceso == false){
				if( banderaGeneral == false){
					bitacoraReproceso.updateStatusRecepcionErrorPromocionPrepago(bitacoraReprocesoVO);
				}
			}
		}
		return respuesta;
	}
	
	private boolean invokePromocion(mx.com.iusacell.comun.xsd.Cliente cliente, Long idPromocion, String secuencia){		
		boolean bandera = false;
		String resultado = Constantes.ERROR;
		String requestXMLData = Utils.objectToXML(cliente, idPromocion);	
		log.info("Antes de notificar la promocion");
		try {
			Boolean respuesta = NotificadorPromocionesPrepago.getInstance().notificaPromocionOnDemand( cliente, idPromocion);
			log.info( "Respuesta del ws de promocion: " + (respuesta != null ? respuesta.booleanValue() : false) + " para dn: " + cliente.getCuenta().getLinea().getDn() + " promocion: " + idPromocion);
			if(respuesta != null){
				bandera = respuesta.booleanValue();
				if(bandera){
					resultado = Constantes.EXITO;
				}
			}
		} catch (RemoteException e) {			
			log.error("Error al invocar al cliente de promociones.", e);		
		} catch (ServiceException e) {			
			log.error("Error al crear singleton de promociones.", e);
		} catch (Exception e) {			
			log.error("Error general en promociones.", e);
		} finally{			
			BitacoraReprocesoDetalleVO  bitacoraReprocesoDetalleVo = llenaObjetos.llenaObjetoBitacoraRecepcionDetalle(Constantes.PROMOCION, resultado, requestXMLData, secuencia, null);		
			bitacoraReprocesoDetalleVo.setDatosSalida(idPromocion.toString());
			bitacoraReproceso.setBitacoraRecepcionDetalle(bitacoraReprocesoDetalleVo);		
		}
		return bandera;
	}
	
	/***
	 * Al objeto linea se le debe setear idLinea, dn, operador, tipo(1prepago, 2pospago, 3hibrido)
	 * @param linea
	 * @return resultado de eliminar promociones 
	 */
	public boolean eliminaPromociones(mx.com.iusacell.comun.xsd.Linea linea){
		boolean bandera = false;		
		try {
			log.info("Se invoco a notificaDesactivacion, linea: " + linea.getId());
			Long respuesta = NotificadorPromocionesPrepago.getInstance().notificaDesactivacion(linea);
			log.info("Respuesta notificaDesactivacion: " + respuesta.intValue() + ", linea: " + linea.getId());
			if(respuesta.intValue() == 0){
				bandera = true;
			}
		} catch (RemoteException e) {
			log.error("Error en desactivacion de promociones RemoteException.",e);				
		} catch (ServiceException e) {
			log.error("Error en desactivacion de promociones ServiceException",e);
		} catch (Exception e) {			
			log.error("Error general en desactivaciones.", e);
		}		
		return bandera;
	}
	
	
	public void setBitacoraReproceso(IBitacoraDAO bitacoraReproceso) {
		this.bitacoraReproceso = bitacoraReproceso;
	}

	public void setSoaPortabilidad(ISoaPortabilidad soaPortabilidad) {
		this.soaPortabilidad = soaPortabilidad;
	}

	public void setCycService(CyCService cycService) {
		this.cycService = cycService;
	}

	public void setLlenaObjetos(ILlenadoObjetos llenaObjetos) {
		this.llenaObjetos = llenaObjetos;
	}

	public void setCompNumeros(INumerosServicio compNumeros) {
		this.compNumeros = compNumeros;
	}
}

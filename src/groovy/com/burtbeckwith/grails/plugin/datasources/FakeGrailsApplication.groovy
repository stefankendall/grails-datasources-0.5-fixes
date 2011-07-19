package com.burtbeckwith.grails.plugin.datasources

import org.apache.log4j.Logger

import org.codehaus.groovy.grails.commons.ApplicationHolder as AH
import org.codehaus.groovy.grails.commons.ArtefactInfo
import org.codehaus.groovy.grails.commons.DefaultGrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClass

/**
 * Used as a mock when calling HibernateGrailsPlugin.
 *
 * @author <a href='mailto:burt@burtbeckwith.com'>Burt Beckwith</a>
 */
class FakeGrailsApplication extends DefaultGrailsApplication {

	final Set<GrailsClass> domainClasses
	private final _fakeDataSource

	protected Logger log = Logger.getLogger(FakeGrailsApplication)

	FakeGrailsApplication(Set<GrailsClass> domainClasses, fakeDataSource) {
		super(domainClasses*.clazz as Class[], AH.application.classLoader)
		this.domainClasses = domainClasses
		_fakeDataSource = fakeDataSource
	}

	@Override
	GrailsClass getArtefact(String artefactType, String name) {
		def artefact = domainClasses.find { it.fullName == name }
		if (!artefact) {
			artefact = super.getArtefact(artefactType, name)
		}
		return artefact
	}

	@Override
	GrailsClass[] getArtefacts(String artefactType) { domainClasses as GrailsClass[] }

	@Override
	GrailsClass addArtefact(String artefactType, GrailsClass artefactGrailsClass) {
		def configuredDomainClasses = _fakeDataSource.domainClasses as Set
		if (configuredDomainClasses.contains(artefactGrailsClass.fullName)) {
			domainClasses << artefactGrailsClass
		}
		return artefactGrailsClass
	}

	@Override
	GrailsClass addArtefact(String artefactType, Class artefactClass) {
		domainClasses.find { dc -> dc.clazz.name ==  artefactClass.name }
	}
}

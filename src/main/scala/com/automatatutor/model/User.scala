package com.automatatutor.model

import net.liftweb.mapper._
import net.liftweb.common.{Full,Box}
import net.liftweb.http.SessionVar
import net.liftweb.util.StringHelpers
import net.liftweb.util.Mailer
import net.liftweb.common.Empty
import net.liftweb.util.FieldError
import scala.xml.Text
import net.liftweb.util.Mailer
import javax.mail.{Authenticator,PasswordAuthentication}
import bootstrap.liftweb.StartupHook
import com.automatatutor.lib.Config

class User extends MegaProtoUser[User] {	

	override def getSingleton = User
	
	def hasStudentRole = Role.findAll(By(Role.userId, this)).exists(role => role.isStudentRole)
	def hasInstructorRole = Role.findAll(By(Role.userId, this)).exists(role => role.isInstructorRole)
	def hasAdminRole = Role.findAll(By(Role.userId, this)).exists(role => role.isAdminRole)
	
	def addStudentRole = Role.createStudentRoleFor(this)
	def addInstructorRole = Role.createInstructorRoleFor(this)
	def addAdminRole = Role.createAdminRoleFor(this)
	
	def removeStudentRole = Role.findAll(By(Role.userId, this)).filter(_.isStudentRole).map(_.delete_!)
	def removeInstructorRole = Role.findAll(By(Role.userId, this)).filter(_.isInstructorRole).map(_.delete_!)
	def removeAdminRole = Role.findAll(By(Role.userId, this)).filter(_.isAdminRole).map(_.delete_!)
	
	def supervisesCourses : Boolean = !Supervision.findByInstructor(this).isEmpty
	def getSupervisedCourses : Seq[Course] = Supervision.findByInstructor(this).map(_.getCourse)
	
	def attendsCourses : Boolean = !Attendance.findAllByUser(this).isEmpty
	def getAttendedCourses : List[Course] = Attendance.findAllByUser(this).map(_.getCourse.openOrThrowException("User should not attend inexistent courses"))
	
	def canBeDeleted : Boolean = !(attendsCourses || supervisesCourses || hasAdminRole)
	def getDeletePreventers : Seq[String] = {
	  val attendancePreventers : Seq[String] = if (this.attendsCourses) List("User still attends courses") else List()
	  val supervisionPreventers : Seq[String] = if (this.supervisesCourses) List("User still supervises courses") else List()
	  val adminPreventers : Seq[String] = if (this.hasAdminRole) List("User is Admin") else List()
	  val preventers = attendancePreventers ++ supervisionPreventers ++ adminPreventers
	  return preventers
	}

	override def delete_! : Boolean = {
	  if (!canBeDeleted) {
	    return false;
	  } else {
	    return super.delete_!
	  }
	}
	
	override def validate = (this.validateFirstName) ++ (this.validateLastName) ++ (super.validate)
	
	private def validateFirstName : List[FieldError] = {
	  if (this.firstName == null || this.firstName.is.isEmpty()) {
	    return List[FieldError](new FieldError(this.firstName, Text("First name must be set")))
	  } else {
	    return List[FieldError]()
	  }
	}
	
	private def validateLastName : List[FieldError] = {
	  if (this.lastName == null || this.lastName.is.isEmpty()) {
	    return List[FieldError](new FieldError(this.lastName, Text("Last name must be set")))
	  } else {
	    return List[FieldError]()
	  }
	}
}

object User extends User with MetaMegaProtoUser[User] with StartupHook {
	// Don't send out emails to users after registration. Remember to set this to false before we go into production
	override def skipEmailValidation = false
	
	// this overridse the noreply@... address that is set by default right now
	// for this to work the correct properties must
	//Mailer.authenticator.map(_.user) openOr 
	override def emailFrom = Config.mail.from.get
	
	// Display the standard template around the User-defined pages
	override def screenWrap = Full(<lift:surround with="default" at="content"><lift:bind /></lift:surround>)

	// Only query given name, family name, email address and password on signup
	override def signupFields = List(firstName, lastName, email, password)

	// Only display given name, family name and email address for editing
	override def editFields = List(firstName, lastName)
	
	override def afterCreate = List(
		(user : User) => { user.addStudentRole },
		(user : User) => { Attendance.finalizePreliminaryEnrollments(user) }
	)
	
	def onStartup = {
	  val adminEmail = Config.admin.email.get
	  val adminPassword = Config.admin.password.get
	  val adminFirstName = Config.admin.firstname.get
	  val adminLastName = Config.admin.lastname.get
	  
	  /* Delete all existing admin accounts, in case there are any leftover from
	   * previous runs */
	  val adminAccounts = User.findAll(By(User.email, adminEmail))
	 
	  //User.bulkDelete_!!(By(User.email, adminEmail))
	  
	  // Create new admin only if the user in the config does not exists	  
	  if (adminAccounts.isEmpty){
		  val adminUser = User.create
		  adminUser.firstName(adminFirstName).lastName(adminLastName).email(adminEmail).password(adminPassword).validated(true).save
		  adminUser.addAdminRole
		  adminUser.addInstructorRole
	  } else {
		  // otherwise just change the password for his account to the one in the config
		  var user = adminAccounts.head
		  var passwordList = List(adminPassword,adminPassword)
		  passwordList
		  user.setPasswordFromListString(passwordList)
		  user.save
	  }
	}

	def findByEmail(email : String) : Box[User] = {
	  val users = User.findAll(By(User.email, email))
	  if(users.size == 1) {
	    return Full(users.head)
	  } else {
	    return Empty
	  }
	}
	
	def currentUser_! : User = {
	  this.currentUser openOrThrowException "This method may only be called if we are certain that a user is logged in"
	}	
	
	def currentUserIdInt : Int = {	  
	  this.currentUserId match { 	  
		case Full(myId) => myId.toInt;
		case _ => 0
		}
	}
}

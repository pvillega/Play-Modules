# App scripts

#All elements must be attached to root to be accessible in the app
root = exports ? this

# Some shared constant values
BACKSPACE = 8
ENTER = 13
SPACE = 32
COMMA = 188

#Enables the proper option in the navigation bar menu by reading the id attribute from the header of the section
root.setNavigationBar = () ->
    $('#menuHomeArea').removeClass('active')
    $('#menuModulesArea').removeClass('active')
    $('#menuDemosArea').removeClass('active')
    $('#menuFeedbackArea').removeClass('active')
    $('#menuProfileArea').removeClass('active')
    $('#menuAuthArea').removeClass('active')
    option = $('header').attr('id')
    if option?
        $('#'+option).addClass('active')
setNavigationBar()

#Function that turns a given element of which we get the id into a tag input component
# id: id of the element to modify
# values: array with valid suggestions
# demo: if true is a demo, otherwise a module (we use it for search by tag)
root.tagInput = (id, values, demo) ->
    #use bootstrap typeahead element
    $('#'+id).typeahead source: values

    $('#'+id).attr('autocomplete','off')

    $('#'+id).keydown (event) ->
            #avoid enter to propagate
            if event.which is ENTER
                event.preventDefault()

    $('#'+id).keyup (event) ->
        #console.log(event.which)
        # Comma/Space/Enter are all valid delimiters for new tags.
        if event.which is COMMA or event.which is SPACE or event.which is ENTER
            event.preventDefault()

            #clean and trim input
            typed = $(this).val()
            typed = typed.replace(/,/g,'')
            typed = typed.trim()

            if typed isnt ""
                addTag(id, typed, false, demo)

            # Cleaning the input.
            $(this).val("")


#Function that adds a tag programmatically to the page, used when a form fails and we want to recover the tags
# id: id of the element that manages tags
# value: value to add
# view: if true we want read only tags
# demo: if true is a demo, otherwise a module (we use it for search by tag)
root.addTag = (id, value, view, demo) ->
      # don't add empty strings
      if not !!value.trim()
        return

      #add tag representation
      el  = "<a class=\"tagLabel\" "

      if not view
        el += " onclick=\"$(this).remove();$('input[data="+value+"]').remove();\" "
      else if demo
        el += " data-pjax=\"#main-container\" href=\""+jsRoutes.controllers.Demos.listDemos(page = 0, orderBy = 1, nameFilter = '', versionFilter = -1, tagFilter = [value]).url+"\" "
      else
        el += " data-pjax=\"#main-container\" href=\""+jsRoutes.controllers.Modules.listModules(page = 0, orderBy = 1, nameFilter = '', versionFilter = -1, tagFilter = [value]).url+"\" "

      el += " >"
      el += value
      if not view
        el += "<span class=\"tagClose\">&nbsp;&nbsp;x</span>"
      el += " </a>"

      #add input control
      if not view
        el += "<input type=\"hidden\" name=\""+id+"[0]\" value=\""+value+"\" data=\""+value+"\">"

      $('.listOfTags.'+id).append(el)

      #renumber the hidden fields
      if not view
        $('input[name^="'+id+'["]').each (i) ->
            newName = $(this).attr('name').replace(/\[.+\]/g, '[' + i + ']')
            $(this).attr('name', newName)

#~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
# Below there are core functional scripts, don't change

# Allows $(function() {}); to be used even without Jquery loaded - This code fragments runs the stored calls.
# This is useful so we can push all the $(function() { ...}); calls in templates without having to load jquery at head
window.$.noConflict()
window.$ = window.$.attachReady(jQuery)

# Enable Pjax on all anchors configured to use it
# Anchors with attribute data-pjax="#main-container" will do Pjax requests
# Example: <a href="#" data-pjax="#main-container">Pjax enabled</a>
$('a[data-pjax]').pjax()

# Prevention of window hijack, run after all jquery scripts
$('html').css 'display': 'none'
if( self == top )
    document.documentElement.style.display = 'block'
else
    top.location = self.location


# Google analytics script, run at the end - Change UA-XXXXX-X to be your site's ID
window._gaq = [['_setAccount', googleAnalyticsCode],['_trackPageview'],['_trackPageLoadTime']];
Modernizr.load load: (if 'https:' == location.protocol then '//ssl' else '//www') + '.google-analytics.com/ga.js'
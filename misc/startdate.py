
# see https://wiki.openstreetmap.org/wiki/Key:start_date#Formatting 
# for start_date syntax
import re
from re import match
# we really extract just YEAR from the start date.
# and it would be nice to extract also interval, to recieve smth like 1978 ± 2


class RegExp:
    Pattern=""
    def Test(self,s):
        result = match(self.Pattern, s)
        return result!=None

def Left(s, pos):
    return s[0:pos]

def Right(s, pos):
    return s[len(s)-pos:len(s)] 

def Mid(s,pos):
    return s[pos-1:len(s)]    


#tests prefix and returns the remainer of the string, if any
def test_prefix(s, prefix):
    remainder = ''
    if s.startswith(prefix):
        l = len(prefix)
        remainder = s[l:]
        
    return remainder.strip()     

#wrapper -- the code expects, that result is string, 
# and original value is returned if unable to parse
def parseStartDateValue(strDate:str) -> str:
    y = parseStartDate(strDate)
    if y is not None:
        s = str(y)
    else:
        s = strDate
    return s


# core function
# return integer if parsed and None if not!
def parseStartDate(strDate:str) -> int:

    strResult = ""
    strModifier = ""
    myRegExp = RegExp()
    
    # remove parenthesis (xxx), especially (xxx ?)
    X = re.findall(r"\(.*\?\)", strDate) 
    for x in X:
        strDate=strDate.replace(x,'').strip() 
    
    
    # empty
    if strDate == '':
        return None 
        
    # list : A;B;C
    # list may occur, if a building has been rebuilt several times or destroyed and then restored.
    if ";" in strDate:
        # we will take the last date, although it is not always the best choice. 
        strDate = strDate.split(";")[-1]
    
          
    #interval XX..YY 
    # XX ans YY can be any tokens (magic!)
    if ".." in strDate:
        tokens = strDate.split("..",1) # 1 equals 2 in python. split in two!
        s1 = parseStartDateValue(tokens[0].strip())
        s2 = parseStartDateValue(tokens[1].strip())
        #print("INTERVAL: " ,int(s1), int(s2))
        try:
            strResult = (int(s1)+int(s2))/2 
           
            strResult = int(strResult)
        except:
            strResult = None          
        return strResult
        
    #1941-1945
    myRegExp.Pattern = '^[0-9]{4}\\s?[-]\\s?[0-9]{4}$'
    if myRegExp.Test(strDate):
        y1 = int(Left(strDate, 4))
        y2 = int(Right(strDate, 4))
        return  int((y1+y2)/2)
  
    #BC 
    myRegExp.Pattern = r'^.*\s?BC$'
    if myRegExp.Test(strDate):
        strDate = strDate[:-3].strip()
             
        strResult = parseStartDateValue(strDate)
        try:
            strResult= -int(strResult) 
        except:
            strResult= None 
            
        return strResult 
  
    #Julian Calendar prefix
    #just ignore it.
    #just 13 days difference is insignificant
    if Left(strDate, 2) == 'j:':
        strDate = Mid(strDate, 3)

    
    #Modifiers
    if  s:=test_prefix(strDate, "~"):
        strDate = s
        
    if  s:=test_prefix(strDate, "before "):        
        strDate = s
        
    if  s:=test_prefix(strDate, "after "):        
        strDate = s    
    
    #we do not need "mid", because we use a middle of the interval anyway.
    if  s:=test_prefix(strDate, "mid "):        
        strDate = s
        
    if  s:=test_prefix(strDate, "early "):
        strDate = s
        strModifier = 'early'
    
    if  s:=test_prefix(strDate, "late "):
        strDate = s
        strModifier = 'late'
    
    if  s:=test_prefix(strDate, "1-я пол."):        
        strDate = s
        strModifier = 'first_half'
    
    if  s:=test_prefix(strDate, "2-я пол."):        
        strDate = s
        strModifier = 'second_half'
    
    if  s:=test_prefix(strDate, "1-я треть "):
        strDate = s
        strModifier = 'first_third'

    if  s:=test_prefix(strDate, "2-я треть "):
        strDate = s
        strModifier = 'second_third'
        
    if  s:=test_prefix(strDate, "3-я треть "):
        strDate = s
        strModifier = 'third_third'
        
    if  s:=test_prefix(strDate, "1-я четв."):
        strDate = s
        strModifier = 'first_quarter'

    if  s:=test_prefix(strDate, "2-я четв."):
        strDate = s
        strModifier = 'second_quarter'
        
    if  s:=test_prefix(strDate, "3-я четв."):
        strDate = s
        strModifier = 'third_quarter'        
        
    if  s:=test_prefix(strDate, "4-я четв."):
        strDate = s
        strModifier = 'forth_quarter'            
        
    
    
    #1234
    myRegExp.Pattern = '^[0-9]{4}$'
    if myRegExp.Test(strDate):
        strResult = strDate
        return int(strResult)
    
    #1234-12
    myRegExp.Pattern = r'^[0-9]{4}-[0-9]{2}$'
    if myRegExp.Test(strDate):
        strResult = Left(strDate,4)
        return int(strResult)
    
    #1234-12-07
    myRegExp.Pattern = '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
    if myRegExp.Test(strDate):
        strResult = Left(strDate, 4)
        return int(strResult)


    #C18
    myRegExp.Pattern = '^C[0-9]{2}$'
    if myRegExp.Test(strDate):
        century_digits = str(int(Mid(strDate, 2)) - 1) 
        if strModifier == "early":
            strResult = century_digits + '10'
        elif strModifier == "late":
            strResult = century_digits + '90'
        
        elif strModifier == "first_half":
            strResult = century_digits + '25'  
            
        elif strModifier == "second_half":
            strResult = century_digits + '75'


        elif strModifier == "first_third":
            strResult = century_digits + '17'
        elif strModifier == "second_third":
            strResult = century_digits + '50'
        elif strModifier == "third_third":
            strResult = century_digits + '84'

        elif strModifier == "first_quarter":
            strResult = century_digits + '12'
        elif strModifier == "second_quarter":
            strResult = century_digits + '38'
        elif strModifier == "third_quarter":
            strResult = century_digits + '62'              
        elif strModifier == "forth_quarter":
            strResult = century_digits + '88'                  
                     
            
        else: 
            strResult = century_digits + '50'
        return int(strResult)    



    #1990s
    myRegExp.Pattern = '^[0-9]{3}0s$'
    if myRegExp.Test(strDate):
        select_variable_0 = strModifier
        if (select_variable_0 == 'early'):
            strResult = Left(strDate, 3) + '2'
        elif (select_variable_0 == 'late'):
            strResult = Left(strDate, 3) + '7'
        else:
            strResult = Left(strDate, 3) + '5'
        
        return int(strResult)
 
                     
    return None

def psd(s):
    y = parseStartDateValue(s)
    try: 
        y=int(y)
    except:
        pass
    print(s + " --> "+str(y))
    return y

def tests():
    print("Tests:")
    
    assert psd("") == ""                             # blank value is still blank
    assert psd("Abrakadabra") == "Abrakadabra"       # the original string if unable to parse
    assert psd("Abrak..adabra") == "Abrak..adabra"   # the original string if unable to parse    
    
    #exact dates 
    assert psd("2010") == 2010                   # where the year is known, but no more
    assert psd("0897") == 897                    #(for 897 CE)
    assert psd("1848-07") == 1848                # where the year and month are known
    assert psd("2010-03-31") == 2010             # for where the full date is known, using the format yyyy-mm-dd
    
    #approximations
    assert psd("~1855") == 1855                  # (some time around 1855)
    assert psd("1860s") == 1865                  # (during the 1860s (i.e 1860 → 1869 inclusive) ==
    assert psd("~1940s") == 1945                 # (probably during the 1940s)
    assert psd("480 BC") == -480                 # (for something that happened in that year)
    assert psd("before 1855") ==  1855           # (during 1854 or before)
    assert psd("before 1910-01-20") == 1910      # (before a specific date - possibly when a photo was taken)
    assert psd("after 1823") == 1823             # (after 1 January 1823 - possibly based on the year in which a map was produced)
    assert psd("C18") == 1750                    # (during the 18th century)
    assert psd("mid C14") == 1350                # (some time during the middle of the 14th century)
    assert psd("late 1920s") == 1927             #   
    assert psd("~C13") == 1250                   # (probably in the 13th century)
    assert psd("1914..1918") == 1916             # indicates some time during WW1.
    assert psd("1914-1918") == 1916              # non standart, but used too frequently to ignore
    assert psd("2008-08-08..2008-08-24") == 2008 # indicates some time during the Beijing Olympics.
    assert psd("C13..C15") == 1350               #
    assert psd("mid C17..late C17") == 1670      #
    assert psd("j:1918-01-31") == 1918           # (a date using the Julian calendar, equivalent to 1918-02-13 in the Gregorian calendar)
    #assert psd("jd:2455511") == #(Using the Julian day system, equivalent to 2010-11-10 in the Gregorian calendar)
    assert psd("753 BC..476") == -138            # ancient Rome period
    
    # Non standard, but used in Russia
    assert psd("C15 (?)") ==  1450 
    assert psd("C18 (1730 ?)") ==  1750 
    
    
    assert psd("1-я пол. C19") ==  1825 
    assert psd("2-я пол. C19") ==  1875
    
    assert psd("1-я треть C16") ==  1517 
    assert psd("2-я треть C16") ==  1550 
    assert psd("3-я треть C16") ==  1584 
    
    assert psd("1-я четв. C16") ==  1512 
    assert psd("2-я четв. C16") ==  1538 
    assert psd("3-я четв. C16") ==  1562
    assert psd("4-я четв. C16") ==  1588 
    
    assert psd("1713..1736;1796;1828") == 1828

   
    print("Tests OK")
    
    
if __name__ == '__main__':
    tests()    
